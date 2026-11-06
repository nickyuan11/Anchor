package com.nickyuan.anchor.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.pm.PackageInfoCompat
import com.nickyuan.anchor.data.SettingsRepository
import com.nickyuan.anchor.data.SkipLogRepository
import com.nickyuan.anchor.data.db.SkipLogEntity
import com.nickyuan.anchor.engine.Debouncer
import com.nickyuan.anchor.engine.FallbackConfig
import com.nickyuan.anchor.engine.FallbackMatcher
import com.nickyuan.anchor.engine.LaunchTracker
import com.nickyuan.anchor.engine.PerLaunchCounter
import com.nickyuan.anchor.engine.RuleDecision
import com.nickyuan.anchor.engine.RuleMatcher
import com.nickyuan.anchor.rule.RuleRepository
import com.nickyuan.anchor.rule.model.AppRules
import com.nickyuan.anchor.rule.model.ClickBy
import com.nickyuan.anchor.snapshot.AppSnapshot
import com.nickyuan.anchor.snapshot.DeviceSnapshot
import com.nickyuan.anchor.snapshot.FloatingCaptureButton
import com.nickyuan.anchor.snapshot.SkipLogSnapshot
import com.nickyuan.anchor.snapshot.Snapshot
import com.nickyuan.anchor.snapshot.SnapshotCapturer
import com.nickyuan.anchor.snapshot.SnapshotRepository
import com.nickyuan.anchor.util.AppLabelProvider
import com.nickyuan.anchor.util.DiagnosticsLog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * 跳过开屏广告的无障碍服务（整个工具的核心入口）。
 *
 * 用户在系统设置里开启后，系统会启动它，并把我们订阅的事件
 * （见 res/xml/accessibility_config.xml）推送到 onAccessibilityEvent。
 *
 * 线程模型（CLAUDE.md 约定"节点遍历不能阻塞主线程"）：
 * - onAccessibilityEvent 在主线程被调用，这里只抄下事件的关键信息，立刻返回；
 * - 真正的工作（读节点树、匹配、点击）交给一个"单线程"协程调度器 engineDispatcher。
 *   单线程保证事件按到达顺序处理，LaunchTracker / Debouncer 也就不需要加锁。
 *
 * @AndroidEntryPoint：让 Hilt 能往这个 Service 里注入依赖（下面的 @Inject 字段）。
 */
@AndroidEntryPoint
class SkipAccessibilityService : AccessibilityService() {

    // 字段注入：Hilt 在 onCreate 时自动赋值，类似 Spring 的 @Autowired 字段
    @Inject lateinit var skipLogRepository: SkipLogRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var snapshotRepository: SnapshotRepository
    @Inject lateinit var appLabelProvider: AppLabelProvider
    @Inject lateinit var diagnostics: DiagnosticsLog
    @Inject lateinit var ruleRepository: RuleRepository

    /**
     * limitedParallelism(1)：从后台线程池里"切"出一个同一时刻只运行一个任务的调度器，
     * 效果等同于单线程，但不用自己创建和管理线程。
     */
    private val engineDispatcher = Dispatchers.Default.limitedParallelism(1)

    /** 协程作用域：服务销毁时统一取消里面所有任务。SupervisorJob 让一个任务出错不影响其他任务 */
    private val scope = CoroutineScope(SupervisorJob() + engineDispatcher)

    /** 主线程作用域：悬浮按钮等界面操作必须在主线程执行 */
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // 规则引擎各部件。时间统一用 uptimeMillis（开机后经过的毫秒数），不受用户修改系统时间影响
    private val launchTracker = LaunchTracker(clock = SystemClock::uptimeMillis)
    private val debouncer = Debouncer(clock = SystemClock::uptimeMillis)
    private val fallbackMatcher = FallbackMatcher()
    private val ruleMatcher = RuleMatcher()
    private val ruleCounter = PerLaunchCounter()

    // 用户设置，由主线程订阅 DataStore 后更新，引擎线程读取。
    // @Volatile：保证一个线程写入后，其他线程马上能读到最新值（和 Java 的 volatile 一样）
    @Volatile private var whitelist: Set<String> = emptySet()
    @Volatile private var fallbackEnabled: Boolean = true
    private lateinit var actionExecutor: ActionExecutor

    /** 是否已有一个"内容变化"任务在排队或执行。用于丢弃密集的重复事件（节流） */
    private val contentScanPending = AtomicBoolean(false)

    /** 缓存"某个类名是不是 Activity"的查询结果，避免重复查询 PackageManager */
    private val activityClassCache = HashMap<String, Boolean>()

    /** 正在做"点击后检查与重试"的包名。期间这个 App 的其他事件不再触发新的点击，避免和重试互相干扰 */
    private var verifyingPackage: String? = null

    // ---- 页面快照（M3） ----
    private lateinit var floatingButton: FloatingCaptureButton
    private val snapshotCapturer = SnapshotCapturer()

    /** 是否正在抓取。防止连点按钮时同时抓好几份 */
    private var capturing = false

    /** 系统完成绑定、服务可以开始工作时调用 */
    override fun onServiceConnected() {
        super.onServiceConnected()
        actionExecutor = ActionExecutor(this)
        floatingButton = FloatingCaptureButton(this, onClick = ::captureSnapshot)

        // 加载规则。放在引擎线程上执行，保证之后的事件处理时规则已经就绪
        scope.launch {
            ruleRepository.load()
            diagnostics.add("已加载 ${ruleRepository.allApps().size} 个 App 的规则" +
                ruleRepository.problems.takeIf { it.isNotEmpty() }?.let { "，问题：$it" }.orEmpty())
        }

        // 订阅"显示快照按钮"设置：用户在 App 里一拨开关，这里就显示或隐藏悬浮按钮
        mainScope.launch {
            settingsRepository.snapshotButtonEnabled.collect { enabled ->
                if (enabled) floatingButton.show() else floatingButton.hide()
            }
        }
        // 订阅白名单和兜底开关，改了立即生效
        mainScope.launch { settingsRepository.whitelist.collect { whitelist = it } }
        mainScope.launch { settingsRepository.fallbackEnabled.collect { fallbackEnabled = it } }
        Log.i(TAG, "跳过服务已启动")
    }

    /** 主线程。只做分发，不做耗时操作 */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // 事件对象在回调结束后会被系统回收复用，所以先把需要的字段抄下来
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString()

        when (event.eventType) {
            // 换页面：每次都处理（数量不多，而且是最早发现开屏广告的时机）
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> scope.launch {
                onWindowStateChanged(packageName, className)
            }

            // 页面内容变化：非常频繁。前一个还没处理完就直接丢弃新的
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (!contentScanPending.compareAndSet(false, true)) return
                scope.launch {
                    try {
                        catchUpForeground(packageName)
                        trySkip(packageName)
                    } finally {
                        contentScanPending.set(false)
                    }
                }
            }

            else -> Unit // 其他事件类型没有在配置里订阅，正常不会收到
        }
    }

    /** 系统要求中断反馈时调用（主要用于读屏类服务），我们没有需要中断的东西 */
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (::floatingButton.isInitialized) floatingButton.hide()
        mainScope.cancel()
        scope.cancel()
        Log.i(TAG, "跳过服务已停止")
        super.onDestroy()
    }

    // ---------------- 以下都运行在 engineDispatcher（单线程）上 ----------------

    private suspend fun onWindowStateChanged(packageName: String, className: String?) {
        val activity = className?.takeIf { isActivity(packageName, it) }
        // 查不到 Activity 时，再看用户正在操作的窗口是不是属于这个 App（插件化 App 靠这一条识别）
        val activeWindowPackage = if (activity == null) activeAppWindowPackage() else null
        val isAppWindow = activity != null || activeWindowPackage == packageName
        val isNewLaunch = launchTracker.onWindowStateChanged(packageName, activity, isAppWindow)

        diagnostics.add(
            "窗口变化 $packageName / $className" +
                (if (activity != null) " [Activity]" else " [非Activity, 活动窗口=$activeWindowPackage]") +
                when {
                    isNewLaunch -> " → 新打开"
                    isAppWindow -> " → 同一App"
                    else -> " → 忽略"
                },
        )
        trySkip(packageName)
    }

    /**
     * 补救：如果某个 App 打开时的"窗口变化"没被识别到，但它的内容变化事件来了，
     * 而且用户正在操作的窗口确实属于它，就补记为一次打开。
     * 只在包名和当前记录的前台 App 不一致时才查询，平时没有额外开销。
     */
    private fun catchUpForeground(packageName: String) {
        if (packageName == launchTracker.foregroundPackage) return
        if (activeAppWindowPackage() != packageName) return
        if (launchTracker.onWindowStateChanged(packageName, null, isAppWindow = true)) {
            diagnostics.add("内容变化补记打开 $packageName")
        }
    }

    /** 当前用户正在操作的 App 窗口属于哪个包；不是 App 窗口（输入法、系统窗口等）时返回 null */
    private fun activeAppWindowPackage(): String? {
        val windowList = windows
        if (windowList.isEmpty()) return rootInActiveWindow?.packageName?.toString()
        return windowList
            .firstOrNull { it.isActive && it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            ?.root?.packageName?.toString()
    }

    /** 尝试在当前页面找到并点击"跳过"：先按专属规则，规则没命中再走通用兜底 */
    private suspend fun trySkip(packageName: String) {
        if (!shouldHandle(packageName)) return
        if (packageName in whitelist) return // 白名单：完全不处理
        if (verifyingPackage == packageName) return

        // 这个 App 的专属规则（没有或被停用则为 null）
        val appRules = ruleRepository.rulesFor(packageName)?.takeIf { it.enabled }
        val ruleWindowMs = appRules?.let(ruleMatcher::maxWindowMs) ?: 0L
        val fallbackWindowMs = if (fallbackEnabled) LaunchTracker.DEFAULT_LAUNCH_WINDOW_MS else 0L
        // 节流：规则和兜底都已过了生效时间，就不看了（design.md 第 2 节"事件节流"）
        if (!launchTracker.isInLaunchWindow(packageName, maxOf(ruleWindowMs, fallbackWindowMs))) return

        // rootInActiveWindow：当前活动窗口的根节点，也就是整棵控件树的入口
        val root = rootInActiveWindow ?: return
        // 事件到达后窗口可能已经切走了，确认还是同一个 App
        if (root.packageName?.toString() != packageName) return

        if (appRules != null && tryRules(packageName, appRules, root)) return
        if (fallbackEnabled && launchTracker.isInLaunchWindow(packageName, fallbackWindowMs)) {
            tryFallback(packageName, root)
        }
    }

    /**
     * 按专属规则处理（design.md 第 6 节）。
     * @return 是否已有规则命中（命中后不再走通用兜底，哪怕因为防抖这次没点）
     */
    private suspend fun tryRules(packageName: String, app: AppRules, root: AccessibilityNodeInfo): Boolean {
        val startedAt = SystemClock.uptimeMillis()
        ruleCounter.syncLaunch(launchTracker.launchCount)
        val screen = Rect().also(root::getBoundsInScreen)
        val decision = ruleMatcher.match(
            app = app,
            activity = launchTracker.currentActivity,
            elapsedSinceLaunchMs = launchTracker.elapsedSinceLaunchMs(),
            page = ServicePageQuery(root),
            screenWidth = screen.width(),
            screenHeight = screen.height(),
            clicksSoFar = ruleCounter::count,
        ) ?: return false

        val rule = decision.rule
        if (!debouncer.tryAcquire("$packageName|${rule.id}")) return true
        ruleCounter.increment(rule.id)

        exclusiveClick(packageName) {
            val activity = launchTracker.currentActivity
            val shake = NodeFinder.containsAnyKeyword(root, FallbackConfig.SHAKE_QUERIES, FallbackConfig.SHAKE_KEYWORDS)
            val searchMs = SystemClock.uptimeMillis() - startedAt

            when (decision) {
                is RuleDecision.ClickNode -> {
                    val node = decision.target.handle
                    val bounds = decision.target.info.bounds
                    val label = decision.target.info.label ?: decision.matchedBy
                    val outcome = when (rule.clickBy) {
                        // 规则指定用手势：直接模拟手指点击，不先试点控件（省掉无效点击后的等待）
                        ClickBy.GESTURE -> actionExecutor.tap(Rect().also(node::getBoundsInScreen), ActionExecutor.DEFAULT_TAP)
                            .let { if (it == GestureResult.COMPLETED) ActionExecutor.ClickOutcome(ClickMethod.GESTURE, it) else null }
                        ClickBy.NODE -> actionExecutor.clickNodeOnly(node)
                        null -> actionExecutor.click(node)
                    }
                    val elapsed = launchTracker.elapsedSinceLaunchMs()
                    diagnostics.add(
                        "规则 ${rule.id} 命中「$label」$bounds（${decision.matchedBy}）打开后${elapsed}ms 查找${searchMs}ms " +
                            "→ ${outcome?.let { "${it.method}${it.gestureResult?.let { r -> "(${r.label})" } ?: ""}" } ?: "点击失败"}",
                    )
                    if (outcome == null) return@exclusiveClick null
                    Log.i(TAG, "规则 ${rule.id} 已点击 $packageName「$label」，方式=${outcome.method}，打开后 ${elapsed}ms，查找 ${searchMs}ms")
                    recordSkip(packageName, activity, rule.id, decision.matchedBy, label, outcome.method, shake, elapsed)
                    // 指定只点控件时不做手势重试；其他情况点完检查，无效就换方式重试
                    if (rule.clickBy == ClickBy.NODE) {
                        null
                    } else {
                        val expected = decision.target.info.label
                        suspend { verifyClick(packageName, activity, rule.id, node, expected, shake) }
                    }
                }

                is RuleDecision.TapPosition -> {
                    val result = actionExecutor.tapAt(decision.x, decision.y)
                    val elapsed = launchTracker.elapsedSinceLaunchMs()
                    diagnostics.add("规则 ${rule.id} 点击坐标(${decision.x},${decision.y}) 打开后${elapsed}ms → ${result.label}")
                    if (result == GestureResult.COMPLETED) {
                        recordSkip(
                            packageName, activity, rule.id, "position", "坐标(${decision.x},${decision.y})",
                            ClickMethod.TAP_POSITION, shake, elapsed,
                        )
                    }
                    null
                }

                is RuleDecision.Back -> {
                    // performGlobalAction：执行系统级操作，这里相当于用户按了一下返回键
                    val ok = performGlobalAction(GLOBAL_ACTION_BACK)
                    val elapsed = launchTracker.elapsedSinceLaunchMs()
                    diagnostics.add("规则 ${rule.id} 按返回键 打开后${elapsed}ms → ${if (ok) "成功" else "失败"}")
                    if (ok) recordSkip(packageName, activity, rule.id, "back", "返回键", ClickMethod.BACK, shake, elapsed)
                    null
                }
            }
        }
        return true
    }

    /** 通用关键词兜底（design.md 第 2 节） */
    private suspend fun tryFallback(packageName: String, root: AccessibilityNodeInfo) {
        val startedAt = SystemClock.uptimeMillis()

        // 1. 快速路径：让目标 App 自己按文字搜索。
        //    每个词都要在目标 App 的界面线程上排一次队，App 刚启动时这个线程很忙，所以词越少越好
        var candidates = NodeFinder.findByText(root, fallbackMatcher.searchKeywords)
        // 页面上有没有广告特征文字、有没有摇一摇类文字；null 表示还没检查过
        var adMarkersPresent: Boolean? = null
        var shakeDetected: Boolean? = null
        var path = "快速"

        // 2. 搜不到时完整遍历（兼容不支持按文字搜索的界面），顺便检查广告特征文字
        if (candidates.isEmpty()) {
            val scan = NodeFinder.scan(root, fallbackMatcher.allKeywords, fallbackMatcher.adMarkers)
            candidates = scan.candidates
            adMarkersPresent = scan.foundMarkers.isNotEmpty()
            shakeDetected = scan.foundMarkers.any { it in FallbackConfig.SHAKE_KEYWORDS }
            path = "完整遍历"
        }
        if (candidates.isEmpty()) {
            Log.v(TAG, "$packageName 未找到候选（$path，${SystemClock.uptimeMillis() - startedAt}ms）")
            return
        }

        // 只有出现了"关闭"这类有条件的候选，才需要查广告特征文字；按常见程度逐个查，查到一个就停
        val infos = candidates.map { it.info }
        if (adMarkersPresent == null && fallbackMatcher.hasConditionalCandidate(infos)) {
            adMarkersPresent = NodeFinder.findFirstText(root, fallbackMatcher.adMarkers) != null
        }

        // 用根节点（整个窗口）的大小作为屏幕尺寸
        val screen = Rect().also(root::getBoundsInScreen)
        val match = fallbackMatcher.findTarget(
            infos, screen.width(), screen.height(), adMarkersPresent = adMarkersPresent == true,
        ) ?: return
        val target = candidates.first { it.info === match.node }

        // 防抖：同一个 App 的兜底规则 2 秒内只点一次。
        // 键里不放页面名：App 打开过程中页面名可能从"未知"变成具体的 Activity，
        // 放进去会被当成两个不同的按钮各点一次（M3 腾讯视频诊断日志里出现过）
        if (!debouncer.tryAcquire("$packageName|$RULE_FALLBACK")) return

        exclusiveClick(packageName) {
            val activity = launchTracker.currentActivity

            // 检测"摇一摇"类文字只为记日志。快速路径没查过的话，点击前补查一次（点完页面就变了）。
            // 用"手机"等 3 个短词代替 7 个完整关键词去查，再核对完整文字，少排几次队
            val shake = shakeDetected
                ?: NodeFinder.containsAnyKeyword(root, FallbackConfig.SHAKE_QUERIES, FallbackConfig.SHAKE_KEYWORDS)

            val searchMs = SystemClock.uptimeMillis() - startedAt
            val outcome = actionExecutor.click(target.node)
            val elapsed = launchTracker.elapsedSinceLaunchMs()
            diagnostics.add(
                "命中 $packageName「${match.matchedText}」${match.node.bounds} 打开后${elapsed}ms 查找${searchMs}ms($path) " +
                    "→ ${outcome?.let { "${it.method}${it.gestureResult?.let { r -> "(${r.label})" } ?: ""}" } ?: "点击失败"}",
            )
            if (outcome == null) {
                Log.w(TAG, "找到了跳过按钮但点击失败：$packageName / ${match.matchedText}")
                return@exclusiveClick null
            }
            Log.i(
                TAG,
                "已跳过 $packageName「${match.matchedText}」，方式=${outcome.method}，打开后 ${elapsed}ms，" +
                    "查找耗时 ${searchMs}ms（$path），摇一摇=$shake",
            )
            recordSkip(packageName, activity, RULE_FALLBACK, match.keyword, match.matchedText, outcome.method, shake, elapsed)

            // 点完检查一下是否真的生效，没生效就换方式重试
            suspend { verifyClick(packageName, activity, RULE_FALLBACK, target.node, match.keyword, shake) }
        }

    }

    /**
     * 在"独占"状态下执行一次点击：从现在起到"检查与重试"结束，这个 App 的其他事件不再触发点击。
     *
     * 标记必须在任何挂起操作（写数据库等）之前设置：挂起期间引擎线程会去处理排队的事件，
     * 如果那时标记还没设置，排队的事件就会抢着再点一次（M3 腾讯视频诊断日志里出现过）。
     *
     * @param action 执行点击并记录；返回点击后要做的检查任务，不需要检查时返回 null
     */
    private suspend fun exclusiveClick(packageName: String, action: suspend () -> (suspend () -> Unit)?) {
        verifyingPackage = packageName
        var handedOver = false
        try {
            val verify = action() ?: return
            scope.launch {
                try {
                    verify()
                } finally {
                    if (verifyingPackage == packageName) verifyingPackage = null
                }
            }
            handedOver = true
        } finally {
            if (!handedOver && verifyingPackage == packageName) verifyingPackage = null
        }
    }

    /**
     * 点击后检查：每隔一会儿看一次，如果按钮还在，说明刚才的点击没生效
     * （有的广告 SDK 只响应真实触摸，M3 腾讯视频快照里遇到过），
     * 就换一种模拟手指点击的方式（按住时长、位置不同）再点，最多重试 [ActionExecutor.RETRY_TAPS] 次。
     *
     * @param expectedText 按钮上应有的文字；为 null 时（例如按 view id 匹配的图片按钮）只检查控件是否还在
     */
    private suspend fun verifyClick(
        packageName: String,
        activity: String?,
        ruleId: String,
        node: AccessibilityNodeInfo,
        expectedText: String?,
        shakeDetected: Boolean,
    ) {
        for ((index, style) in ActionExecutor.RETRY_TAPS.withIndex()) {
            delay(VERIFY_DELAY_MS) // 挂起等待，不占用引擎线程，期间其他事件照常处理
            if (launchTracker.foregroundPackage != packageName) return
            if (rootInActiveWindow?.packageName?.toString() != packageName) return
            // refresh()：从目标 App 重新读取这个控件的最新状态；返回 false 表示控件已经不存在
            if (!node.refresh() || !node.isVisibleToUser) {
                diagnostics.add("按钮已消失，第${index}次重试后生效 $packageName")
                return
            }
            val label = (node.text ?: node.contentDescription)?.toString()
            // 文字变了，说明已经不是原来的按钮了（倒计时数字的变化不算，"跳过 5"→"跳过 4" 仍是同一个按钮）
            if (expectedText != null && !sameButtonText(label, expectedText)) return

            val rect = Rect().also(node::getBoundsInScreen)
            val result = actionExecutor.tap(rect, style)
            val elapsed = launchTracker.elapsedSinceLaunchMs()
            diagnostics.add("按钮仍在，第${index + 1}次重试 $style → ${result.label}，打开后${elapsed}ms")
            Log.i(TAG, "点击后按钮仍在，第${index + 1}次重试（$style）：$packageName「$label」→ ${result.label}")
            if (result == GestureResult.COMPLETED) {
                recordSkip(
                    packageName, activity, ruleId, expectedText ?: "", label ?: "", ClickMethod.GESTURE_RETRY,
                    shakeDetected, elapsed,
                )
            }
        }
        delay(VERIFY_DELAY_MS)
        val stillThere = node.refresh() && node.isVisibleToUser
        diagnostics.add(if (stillThere) "重试用完，按钮仍在 $packageName" else "按钮已消失，最后一次重试后生效 $packageName")
    }

    /** 去掉数字和空白后比较，忽略倒计时的变化 */
    private fun sameButtonText(label: String?, expected: String): Boolean {
        if (label == null) return false
        fun String.core() = filterNot { it.isDigit() || it.isWhitespace() }
        return label.core().contains(expected.core(), ignoreCase = true)
    }

    private suspend fun recordSkip(
        packageName: String,
        activity: String?,
        ruleId: String,
        keyword: String,
        matchedText: String,
        method: ClickMethod,
        shakeDetected: Boolean,
        elapsed: Long,
    ) {
        skipLogRepository.record(
            SkipLogEntity(
                timestamp = System.currentTimeMillis(),
                packageName = packageName,
                activityName = activity,
                ruleId = ruleId,
                matchedKeyword = keyword,
                matchedText = matchedText,
                clickMethod = method.name,
                shakeDetected = shakeDetected,
                elapsedSinceLaunchMs = elapsed,
            ),
        )
    }

    /**
     * 是否处理这个包。
     * 本 App 自己的页面一律不处理——唯一例外是 debug 包里的模拟广告页，否则没法测试。
     */
    private fun shouldHandle(packageName: String): Boolean {
        if (packageName != this.packageName) return true
        return launchTracker.currentActivity == FAKE_AD_ACTIVITY
    }

    /**
     * 判断窗口类名是不是一个 Activity。
     * 换页面事件的 className 可能是 Activity，也可能是弹窗、菜单等控件类名，需要区分。
     */
    private fun isActivity(packageName: String, className: String): Boolean =
        activityClassCache.getOrPut("$packageName/$className") {
            try {
                packageManager.getActivityInfo(ComponentName(packageName, className), 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }

    // ---------------- 页面快照（M3） ----------------

    /**
     * 点击悬浮按钮后调用（主线程）。依次：
     * 1. 在引擎线程读取所有窗口的控件树；
     * 2. 隐藏悬浮按钮，截图，再显示回来；
     * 3. 附上手机信息和这个 App 最近的跳过记录，写成 zip。
     */
    private fun captureSnapshot() {
        if (capturing) return
        capturing = true
        mainScope.launch {
            try {
                val capturedAt = LocalDateTime.now()

                // 1. 控件树：放到引擎线程，和跳过逻辑共用同一个单线程，读 launchTracker 也不用加锁
                val (windowsSnapshot, appSnapshot) = withContext(engineDispatcher) {
                    val activeRoot = rootInActiveWindow
                    val packageName = activeRoot?.packageName?.toString()
                        ?: launchTracker.foregroundPackage
                        ?: "unknown"
                    val trackerMatches = packageName == launchTracker.foregroundPackage
                    snapshotCapturer.captureWindows(windows, activeRoot) to buildAppSnapshot(
                        packageName = packageName,
                        activity = if (trackerMatches) launchTracker.currentActivity else null,
                        elapsedMs = if (trackerMatches) launchTracker.elapsedSinceLaunchMs() else null,
                    )
                }

                // 2. 截图：先隐藏按钮，等一帧画面刷新（约 150ms）再截
                floatingButton.setTemporarilyHidden(true)
                delay(150)
                val screenshot = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) takeScreenshotJpeg() else null
                floatingButton.setTemporarilyHidden(false)

                // 3. 组装并保存
                val logs = skipLogRepository.recentFor(appSnapshot.packageName).map {
                    SkipLogSnapshot(
                        timestamp = it.timestamp,
                        activity = it.activityName,
                        ruleId = it.ruleId,
                        matchedText = it.matchedText,
                        clickMethod = it.clickMethod,
                        shakeDetected = it.shakeDetected,
                        elapsedSinceLaunchMs = it.elapsedSinceLaunchMs,
                    )
                }
                val snapshot = Snapshot(
                    capturedAt = capturedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    app = appSnapshot,
                    device = buildDeviceSnapshot(),
                    windows = windowsSnapshot,
                    recentSkipLogs = logs,
                    hasScreenshot = screenshot != null,
                    diagnostics = diagnostics.snapshot(),
                )
                snapshotRepository.save(snapshot, screenshot, capturedAt)
                toast("已保存快照：${appSnapshot.appName}")
            } catch (e: Exception) {
                Log.e(TAG, "抓取快照失败", e)
                toast("抓取快照失败：${e.message}")
            } finally {
                floatingButton.setTemporarilyHidden(false)
                capturing = false
            }
        }
    }

    private fun buildAppSnapshot(packageName: String, activity: String?, elapsedMs: Long?): AppSnapshot {
        val packageInfo = runCatching { packageManager.getPackageInfo(packageName, 0) }.getOrNull()
        return AppSnapshot(
            packageName = packageName,
            appName = appLabelProvider.labelOf(packageName),
            versionName = packageInfo?.versionName,
            versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) },
            activity = activity,
            elapsedSinceLaunchMs = elapsedMs,
        )
    }

    private fun buildDeviceSnapshot(): DeviceSnapshot {
        // 屏幕的完整尺寸（包括状态栏、导航栏）
        val (width, height) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            resources.displayMetrics.let { it.widthPixels to it.heightPixels }
        }
        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            brand = Build.BRAND,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            buildDisplay = Build.DISPLAY,
            screenWidth = width,
            screenHeight = height,
        )
    }

    /**
     * 用无障碍服务自带的截屏能力截一张图，压缩成 JPEG。失败时返回 null。
     *
     * suspendCancellableCoroutine：把"传回调"风格的异步接口，包装成可以直接 await 的挂起函数，
     * 类似把 Java 的 Callback 包成 CompletableFuture 再 get()，但不会阻塞线程。
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeScreenshotJpeg(): ByteArray? = suspendCancellableCoroutine { cont ->
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            Dispatchers.Default.asExecutor(), // 回调在后台线程执行，压缩图片不卡界面
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val jpeg = runCatching {
                        result.hardwareBuffer.use { buffer ->
                            // 系统给的是显存里的图像，先复制成普通 Bitmap 才能压缩
                            val hardwareBitmap = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                ?: return@use null
                            val bitmap = hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                            hardwareBitmap.recycle()
                            ByteArrayOutputStream().also {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)
                                bitmap.recycle()
                            }.toByteArray()
                        }
                    }.getOrNull()
                    cont.resume(jpeg)
                }

                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "截图失败，错误码 $errorCode")
                    cont.resume(null)
                }
            },
        )
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private companion object {
        /**
         * 点击后等多久检查按钮是否还在。
         * 0.3.2 用的是 1 秒；腾讯视频直接点控件无效，等 1 秒白白耽误时间，改为 0.5 秒
         */
        const val VERIFY_DELAY_MS = 500L
        const val JPEG_QUALITY = 85
        const val TAG = "SkipService"
        const val RULE_FALLBACK = "fallback"
        /** debug 包里的模拟广告页（M1），release 包里不存在这个类 */
        const val FAKE_AD_ACTIVITY = "com.nickyuan.anchor.debug.FakeSplashAdActivity"
    }
}

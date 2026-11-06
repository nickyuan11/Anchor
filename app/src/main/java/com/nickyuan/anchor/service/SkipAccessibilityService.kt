package com.nickyuan.anchor.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.nickyuan.anchor.data.SkipLogRepository
import com.nickyuan.anchor.data.db.SkipLogEntity
import com.nickyuan.anchor.engine.Debouncer
import com.nickyuan.anchor.engine.FallbackMatcher
import com.nickyuan.anchor.engine.LaunchTracker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

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

    /**
     * limitedParallelism(1)：从后台线程池里"切"出一个同一时刻只运行一个任务的调度器，
     * 效果等同于单线程，但不用自己创建和管理线程。
     */
    private val engineDispatcher = Dispatchers.Default.limitedParallelism(1)

    /** 协程作用域：服务销毁时统一取消里面所有任务。SupervisorJob 让一个任务出错不影响其他任务 */
    private val scope = CoroutineScope(SupervisorJob() + engineDispatcher)

    // 规则引擎各部件。时间统一用 uptimeMillis（开机后经过的毫秒数），不受用户修改系统时间影响
    private val launchTracker = LaunchTracker(clock = SystemClock::uptimeMillis)
    private val debouncer = Debouncer(clock = SystemClock::uptimeMillis)
    private val fallbackMatcher = FallbackMatcher()
    private lateinit var actionExecutor: ActionExecutor

    /** 是否已有一个"内容变化"任务在排队或执行。用于丢弃密集的重复事件（节流） */
    private val contentScanPending = AtomicBoolean(false)

    /** 缓存"某个类名是不是 Activity"的查询结果，避免重复查询 PackageManager */
    private val activityClassCache = HashMap<String, Boolean>()

    /** 系统完成绑定、服务可以开始工作时调用 */
    override fun onServiceConnected() {
        super.onServiceConnected()
        actionExecutor = ActionExecutor(this)
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
        scope.cancel()
        Log.i(TAG, "跳过服务已停止")
        super.onDestroy()
    }

    // ---------------- 以下都运行在 engineDispatcher（单线程）上 ----------------

    private suspend fun onWindowStateChanged(packageName: String, className: String?) {
        val activity = className?.takeIf { isActivity(packageName, it) }
        launchTracker.onWindowStateChanged(packageName, activity)
        trySkip(packageName)
    }

    /** 尝试在当前页面找到并点击"跳过" */
    private suspend fun trySkip(packageName: String) {
        if (!shouldHandle(packageName)) return
        // 节流：不在启动窗口期内就不看了（design.md 第 2 节"事件节流"）
        if (!launchTracker.isInLaunchWindow(packageName)) return

        // rootInActiveWindow：当前活动窗口的根节点，也就是整棵控件树的入口
        val root = rootInActiveWindow ?: return
        // 事件到达后窗口可能已经切走了，确认还是同一个 App
        if (root.packageName?.toString() != packageName) return

        val scan = NodeFinder.scan(root, fallbackMatcher.keywords)
        if (scan.candidates.isEmpty()) return

        // 用根节点（整个窗口）的大小作为屏幕尺寸
        val screen = Rect().also(root::getBoundsInScreen)
        val match = fallbackMatcher.findTarget(
            scan.candidates.map { it.info }, screen.width(), screen.height(),
        ) ?: return
        val target = scan.candidates.first { it.info === match.node }

        val activity = launchTracker.currentActivity
        if (!debouncer.tryAcquire("$packageName|$activity|$RULE_FALLBACK")) return

        val method = actionExecutor.click(target.node) ?: run {
            Log.w(TAG, "找到了跳过按钮但点击失败：$packageName / ${match.matchedText}")
            return
        }
        val elapsed = launchTracker.elapsedSinceLaunchMs()
        Log.i(TAG, "已跳过 $packageName「${match.matchedText}」，方式=$method，用时 ${elapsed}ms，摇一摇=${scan.shakeDetected}")

        skipLogRepository.record(
            SkipLogEntity(
                timestamp = System.currentTimeMillis(),
                packageName = packageName,
                activityName = activity,
                ruleId = RULE_FALLBACK,
                matchedKeyword = match.keyword,
                matchedText = match.matchedText,
                clickMethod = method.name,
                shakeDetected = scan.shakeDetected,
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

    private companion object {
        const val TAG = "SkipService"
        const val RULE_FALLBACK = "fallback"
        /** debug 包里的模拟广告页（M1），release 包里不存在这个类 */
        const val FAKE_AD_ACTIVITY = "com.nickyuan.anchor.debug.FakeSplashAdActivity"
    }
}

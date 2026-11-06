package com.nickyuan.anchor.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** 点击是怎么完成的，会记进跳过记录，方便排查 */
enum class ClickMethod(val label: String) {
    NODE("点击控件"),
    PARENT("点击父控件"),
    GESTURE("模拟手势"),

    /** 第一次点击后按钮还在（有些广告只认真实触摸），改用模拟手势又点了一次 */
    GESTURE_RETRY("点击无效，改用模拟手势"),

    /** 规则指定的屏幕坐标点击 */
    TAP_POSITION("点击坐标"),

    /** 规则指定的返回键 */
    BACK("返回键"),
}

/** 模拟手势的执行结果 */
enum class GestureResult(val label: String) {
    COMPLETED("已执行"),
    CANCELLED("被系统取消"),
    REJECTED("系统拒绝发出"),
    TIMEOUT("等待结果超时"),
}

/**
 * 一种"模拟手指点击"的方式：点在控件的哪个位置、按住多久。
 * 有的广告 SDK 会识别"太标准"的点击（永远点正中间、按下时长固定），所以重试时换着来。
 *
 * @param xFraction、yFraction 点击位置在控件内的比例，0.5 表示正中间
 * @param durationMs 按住多少毫秒后抬起
 */
data class TapStyle(val xFraction: Float, val yFraction: Float, val durationMs: Long) {
    override fun toString() = "位置(${xFraction},${yFraction}) 按住${durationMs}ms"
}

/**
 * 执行点击（design.md 第 2 节第 5 步），按顺序尝试：
 * 1. 控件本身可点击 → 直接让系统点它（performAction）；
 * 2. 不可点击 → 往上找最近的可点击父控件（很多按钮是"外层可点、里层是文字"）；
 * 3. 还不行 → 在控件位置模拟一次手指点击（dispatchGesture）。
 */
class ActionExecutor(private val service: AccessibilityService) {

    /** 一次点击尝试的结果 */
    data class ClickOutcome(val method: ClickMethod, val gestureResult: GestureResult? = null)

    /** 依次尝试三种方式。成功时返回所用的方式，全部失败返回 null */
    suspend fun click(node: AccessibilityNodeInfo): ClickOutcome? {
        clickNodeOnly(node)?.let { return it }
        val result = tap(Rect().also(node::getBoundsInScreen), DEFAULT_TAP)
        return if (result == GestureResult.COMPLETED) ClickOutcome(ClickMethod.GESTURE, result) else null
    }

    /** 在屏幕坐标 (x, y) 处模拟一次点击 */
    suspend fun tapAt(x: Int, y: Int, durationMs: Long = DEFAULT_TAP.durationMs): GestureResult =
        tap(Rect(x, y, x + 1, y + 1), TapStyle(0f, 0f, durationMs))

    /** 只用前两种方式（点控件本身、点可点击的父控件），不模拟手势 */
    fun clickNodeOnly(node: AccessibilityNodeInfo): ClickOutcome? {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return ClickOutcome(ClickMethod.NODE)
        }
        var parent = node.parent
        var depth = 0
        while (parent != null && depth < MAX_PARENT_DEPTH) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return ClickOutcome(ClickMethod.PARENT)
            }
            parent = parent.parent
            depth++
        }
        return null
    }

    /**
     * 在 [bounds] 区域内按 [style] 模拟一次手指点击，并等待系统报告结果。
     * 需要在 accessibility_config.xml 里开启 canPerformGestures。
     */
    suspend fun tap(bounds: Rect, style: TapStyle): GestureResult {
        if (bounds.isEmpty) return GestureResult.REJECTED
        val x = bounds.left + bounds.width() * style.xFraction
        val y = bounds.top + bounds.height() * style.yFraction
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(Path().apply { moveTo(x, y) }, 0, style.durationMs))
            .build()

        // 把"传回调"的接口包装成挂起函数，最多等 1 秒
        return withTimeoutOrNull(GESTURE_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val callback = object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription) {
                        if (cont.isActive) cont.resume(GestureResult.COMPLETED)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription) {
                        if (cont.isActive) cont.resume(GestureResult.CANCELLED)
                    }
                }
                // handler 传 null：回调在主线程执行
                if (!service.dispatchGesture(gesture, callback, null)) {
                    cont.resume(GestureResult.REJECTED)
                }
            }
        } ?: GestureResult.TIMEOUT
    }

    companion object {
        private const val MAX_PARENT_DEPTH = 5
        private const val GESTURE_TIMEOUT_MS = 1_000L

        /**
         * 默认的模拟点击：正中间按住 100ms。
         * 腾讯视频的跳过按钮对按住 50ms 的模拟点击没反应，100ms 有效（M3 真机诊断日志）
         */
        val DEFAULT_TAP = TapStyle(0.5f, 0.5f, 100)

        /** 点击无效后依次尝试的方式：正中间 100ms（腾讯视频实测有效）、换个位置、按得更久 */
        val RETRY_TAPS = listOf(
            DEFAULT_TAP,
            TapStyle(0.35f, 0.6f, 70),
            TapStyle(0.6f, 0.4f, 200),
        )
    }
}

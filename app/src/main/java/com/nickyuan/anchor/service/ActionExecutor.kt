package com.nickyuan.anchor.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** 点击是怎么完成的，会记进跳过记录，方便排查 */
enum class ClickMethod(val label: String) {
    NODE("点击控件"),
    PARENT("点击父控件"),
    GESTURE("模拟手势"),
}

/**
 * 执行点击（design.md 第 2 节第 5 步），按顺序尝试：
 * 1. 控件本身可点击 → 直接让系统点它（performAction）；
 * 2. 不可点击 → 往上找最近的可点击父控件（很多按钮是"外层可点、里层是文字"）；
 * 3. 还不行 → 在控件中心位置模拟一次手指点击（dispatchGesture）。
 */
class ActionExecutor(private val service: AccessibilityService) {

    /** @return 成功时返回所用的方式，全部失败返回 null */
    fun click(node: AccessibilityNodeInfo): ClickMethod? {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return ClickMethod.NODE
        }

        var parent = node.parent
        var depth = 0
        while (parent != null && depth < MAX_PARENT_DEPTH) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return ClickMethod.PARENT
            }
            parent = parent.parent
            depth++
        }

        val rect = Rect().also(node::getBoundsInScreen)
        return if (tap(rect.exactCenterX(), rect.exactCenterY())) ClickMethod.GESTURE else null
    }

    /**
     * 在屏幕坐标 (x, y) 处模拟一次点击：手指按下，停留 50 毫秒，抬起。
     * 需要在 accessibility_config.xml 里开启 canPerformGestures。
     */
    fun tap(x: Float, y: Float): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        return service.dispatchGesture(gesture, null, null)
    }

    private companion object {
        const val MAX_PARENT_DEPTH = 5
        const val TAP_DURATION_MS = 50L
    }
}

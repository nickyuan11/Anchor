package com.nickyuan.anchor.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * 保活用的隐形悬浮窗：屏幕左上角一个 1 像素、几乎全透明、点不到的小窗口。
 *
 * 为什么需要它：红米 K70（澎湃 OS）的朋友反馈，只有打开 📷 悬浮按钮时才能稳定跳过。
 * 推断原因是澎湃 OS 会冻结"没有可见窗口"的后台进程，冻结期间收不到页面变化事件；
 * 屏幕上有我们的窗口时，进程就不会被冻结。所以服务运行期间一直保留这个看不见的小窗口。
 *
 * 和 📷 按钮一样使用无障碍悬浮层（TYPE_ACCESSIBILITY_OVERLAY），不需要悬浮窗权限。
 * 所有方法必须在主线程调用。
 */
class KeepAliveOverlay(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: View? = null

    fun show() {
        if (view != null) return
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // 不抢焦点、不接收触摸：触摸会直接穿过它，落到下面的 App 上
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }

        // 透明度 1/255：肉眼看不见，但系统仍把它当作一个"画了内容"的窗口
        val dot = View(service).apply { setBackgroundColor(Color.argb(1, 0, 0, 0)) }
        windowManager.addView(dot, params)
        view = dot
    }

    fun hide() {
        view?.let { windowManager.removeView(it) }
        view = null
    }
}

package com.nickyuan.anchor.snapshot

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * 悬浮在所有 App 上面的 📷 按钮：点一下抓取页面快照，按住可以拖动。
 *
 * 用的是无障碍服务专属的悬浮层窗口（TYPE_ACCESSIBILITY_OVERLAY）：
 * 只要无障碍服务开着就能显示，不需要用户再单独授予"悬浮窗"权限。
 *
 * 这里没用 Compose，而是用传统的 Android View（TextView）：
 * 在服务里使用 Compose 需要额外补齐生命周期支持，对一个小按钮来说不划算。
 * View 是 Android 早期的界面写法，概念上和 Compose 的控件类似，只是要手动创建和更新。
 *
 * 所有方法都必须在主线程调用（Android 规定界面只能在主线程操作）。
 */
class FloatingCaptureButton(
    private val service: AccessibilityService,
    private val onClick: () -> Unit,
) {
    // WindowManager：系统的"窗口管理器"，悬浮窗就是通过它添加到屏幕上的
    private val windowManager = service.getSystemService(WindowManager::class.java)
    private var view: TextView? = null

    private val sizePx = dp(52)

    /** 悬浮窗的位置和属性 */
    private val params = WindowManager.LayoutParams(
        sizePx,
        sizePx,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        // NOT_FOCUSABLE：不抢键盘焦点，不影响下面 App 的输入框
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT, // 支持半透明
    ).apply {
        gravity = Gravity.TOP or Gravity.START // x、y 从屏幕左上角算起
        val metrics = service.resources.displayMetrics
        x = metrics.widthPixels - sizePx - dp(8) // 默认贴在右边
        y = (metrics.heightPixels * 0.4).toInt() // 屏幕高度 40% 的位置
    }

    val isShowing: Boolean get() = view != null

    // 拖动监听在松开时会调用 performClick()，读屏软件仍能正常点击，所以可以忽略这条 lint 警告
    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null) return
        val button = TextView(service).apply {
            text = "📷"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            contentDescription = "抓取页面快照"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(150, 0, 0, 0)) // 半透明黑色圆形
            }
            setOnClickListener { onClick() }
            setOnTouchListener(DragListener())
        }
        windowManager.addView(button, params)
        view = button
    }

    fun hide() {
        view?.let { windowManager.removeView(it) }
        view = null
    }

    /** 截图前临时隐藏按钮，避免它出现在截图里；截完再显示回来 */
    fun setTemporarilyHidden(hidden: Boolean) {
        view?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    /**
     * 处理拖动：手指移动超过"误触距离"就算拖动，否则松开时算一次点击。
     */
    private inner class DragListener : View.OnTouchListener {
        private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop
        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        windowManager.updateViewLayout(v, params)
                    }
                }

                MotionEvent.ACTION_UP -> if (!dragging) v.performClick()
            }
            return true
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), service.resources.displayMetrics).toInt()
}

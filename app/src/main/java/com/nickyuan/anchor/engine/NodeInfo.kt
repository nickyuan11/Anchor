package com.nickyuan.anchor.engine

/**
 * 控件在屏幕上的矩形区域，单位是像素，原点在屏幕左上角。
 */
data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}

/**
 * 一个控件（节点）的只读快照。
 *
 * 系统给的控件对象 AccessibilityNodeInfo 是 Android 类，没法在电脑上直接做单元测试。
 * 所以先把需要的字段抄到这个普通数据类里，规则引擎只认它，不依赖 Android。
 *
 * @param text 控件上显示的文字
 * @param contentDescription 内容描述：给读屏软件念的文字，有些图片按钮只有它没有 text
 * @param viewId 控件的资源 id，例如 "com.example:id/skip_btn"
 * @param isClickable 控件本身是否可点击
 * @param isVisibleToUser 用户此刻是否看得到它
 */
data class NodeInfo(
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val bounds: Bounds,
    val isClickable: Boolean,
    val isVisibleToUser: Boolean,
) {
    /** 用于匹配的文字：优先 text，没有就用 contentDescription */
    val label: String?
        get() = text?.takeIf { it.isNotBlank() } ?: contentDescription?.takeIf { it.isNotBlank() }
}

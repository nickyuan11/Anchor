package com.nickyuan.anchor.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.nickyuan.anchor.engine.Bounds
import com.nickyuan.anchor.engine.NodeInfo

/**
 * 在页面的控件树（节点树）里找候选控件。
 *
 * 页面上的所有控件组成一棵树：根节点是整个窗口，子节点是里面的布局、文字、按钮……
 * 这里用广度优先遍历走一遍，一次遍历同时做两件事：
 * 1. 收集文字含关键词的控件，作为"跳过"候选；
 * 2. 检查页面上有没有"摇一摇"字样。
 *
 * 为什么不用系统自带的 findAccessibilityNodeInfosByText：
 * 它对 Compose、部分自绘界面支持不完整，可能漏掉按钮；自己遍历更可靠。
 * 开屏广告页的控件通常只有几十个，遍历很快。
 */
object NodeFinder {

    /** 候选控件：引擎用的只读快照 + 系统原始节点（点击时要用原始节点） */
    class Candidate(val info: NodeInfo, val node: AccessibilityNodeInfo)

    class ScanResult(val candidates: List<Candidate>, val shakeDetected: Boolean)

    /**
     * @param keywords 跳过关键词，文字含其中之一就成为候选（英文不区分大小写）
     * @param shakeKeywords 摇一摇关键词
     * @param maxNodes 最多遍历多少个节点，防止遇到超大页面时耗时过长
     */
    fun scan(
        root: AccessibilityNodeInfo,
        keywords: List<String>,
        shakeKeywords: List<String> = DEFAULT_SHAKE_KEYWORDS,
        maxNodes: Int = DEFAULT_MAX_NODES,
    ): ScanResult {
        val candidates = mutableListOf<Candidate>()
        var shakeDetected = false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++

            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()

            if (!shakeDetected && (text.containsAny(shakeKeywords) || desc.containsAny(shakeKeywords))) {
                shakeDetected = true
            }
            if (text.containsAny(keywords) || desc.containsAny(keywords)) {
                candidates += Candidate(node.toNodeInfo(text, desc), node)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return ScanResult(candidates, shakeDetected)
    }

    /** 把系统节点转成引擎用的 NodeInfo */
    private fun AccessibilityNodeInfo.toNodeInfo(text: String?, desc: String?): NodeInfo {
        val rect = Rect()
        getBoundsInScreen(rect)
        return NodeInfo(
            text = text,
            contentDescription = desc,
            viewId = viewIdResourceName,
            bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            isClickable = isClickable,
            isVisibleToUser = isVisibleToUser,
        )
    }

    private fun String?.containsAny(words: List<String>): Boolean =
        this != null && words.any { contains(it, ignoreCase = true) }

    val DEFAULT_SHAKE_KEYWORDS = listOf("摇一摇", "摇动手机", "扭动手机")
    private const val DEFAULT_MAX_NODES = 500
}

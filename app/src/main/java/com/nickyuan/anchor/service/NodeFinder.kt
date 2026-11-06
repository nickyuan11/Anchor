package com.nickyuan.anchor.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.nickyuan.anchor.engine.Bounds
import com.nickyuan.anchor.engine.NodeInfo

/**
 * 在页面的控件树（节点树）里找候选控件。
 *
 * 页面上的所有控件组成一棵树：根节点是整个窗口，子节点是里面的布局、文字、按钮……
 * 我们的服务和目标 App 不在同一个进程，读每个控件都是一次"跨进程调用"，有一定开销。
 * 页面越大（比如 B 站首页有 200 多个控件），逐个读就越慢。所以分两条路：
 *
 * 1. 快速路径 [findByText]：把关键词交给目标 App，让它在自己进程里搜索，只返回命中的控件。
 *    每个关键词一次跨进程调用，和页面大小几乎无关。对传统 View 界面有效。
 * 2. 完整遍历 [scan]：快速路径搜不到时再用。有些界面（Compose、部分自绘界面）
 *    不支持按文字搜索，只能自己从根节点开始逐个读。
 */
object NodeFinder {

    /** 候选控件：引擎用的只读快照 + 系统原始节点（点击时要用原始节点） */
    class Candidate(val info: NodeInfo, val node: AccessibilityNodeInfo)

    /**
     * @param candidates 文字含关键词的控件
     * @param foundMarkers 遍历过程中在页面上看到的广告特征文字
     */
    class ScanResult(val candidates: List<Candidate>, val foundMarkers: Set<String>)

    /** 快速路径：按文字搜索（系统会同时匹配 text 和内容描述，不区分大小写，包含即可） */
    fun findByText(root: AccessibilityNodeInfo, words: List<String>): List<Candidate> {
        // LinkedHashSet：去重（同一个控件可能被多个关键词命中），并保持顺序
        val nodes = LinkedHashSet<AccessibilityNodeInfo>()
        for (word in words) {
            nodes += root.findAccessibilityNodeInfosByText(word).orEmpty()
        }
        return nodes.map { Candidate(it.toNodeInfo(), it) }
    }

    /** 页面上有没有出现这些文字中的任意一个（快速路径） */
    fun findAnyText(root: AccessibilityNodeInfo, words: List<String>): Set<String> =
        words.filterTo(LinkedHashSet()) { root.findAccessibilityNodeInfosByText(it).orEmpty().isNotEmpty() }

    /**
     * 完整遍历：广度优先走一遍控件树，同时收集候选控件和广告特征文字。
     *
     * @param keywords 跳过关键词，文字含其中之一就成为候选（英文不区分大小写）
     * @param markers 要顺便检查的广告特征文字
     * @param maxNodes 最多遍历多少个节点，防止遇到超大页面时耗时过长
     */
    fun scan(
        root: AccessibilityNodeInfo,
        keywords: List<String>,
        markers: List<String>,
        maxNodes: Int = DEFAULT_MAX_NODES,
    ): ScanResult {
        val candidates = mutableListOf<Candidate>()
        val foundMarkers = LinkedHashSet<String>()

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < maxNodes) {
            val node = queue.removeFirst()
            visited++

            val text = node.text?.toString()
            val desc = node.contentDescription?.toString()

            for (marker in markers) {
                if (text.containsWord(marker) || desc.containsWord(marker)) foundMarkers += marker
            }
            if (keywords.any { text.containsWord(it) || desc.containsWord(it) }) {
                candidates += Candidate(node.toNodeInfo(), node)
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(queue::add)
            }
        }
        return ScanResult(candidates, foundMarkers)
    }

    /** 把系统节点转成引擎用的 NodeInfo */
    private fun AccessibilityNodeInfo.toNodeInfo(): NodeInfo {
        val rect = Rect()
        getBoundsInScreen(rect)
        return NodeInfo(
            text = text?.toString(),
            contentDescription = contentDescription?.toString(),
            viewId = viewIdResourceName,
            bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            isClickable = isClickable,
            isVisibleToUser = isVisibleToUser,
        )
    }

    private fun String?.containsWord(word: String): Boolean =
        this != null && contains(word, ignoreCase = true)

    private const val DEFAULT_MAX_NODES = 500
}

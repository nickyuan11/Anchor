package com.nickyuan.anchor.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.nickyuan.anchor.engine.Bounds
import com.nickyuan.anchor.engine.FoundNode
import com.nickyuan.anchor.engine.NodeInfo
import com.nickyuan.anchor.engine.PageQuery

/**
 * 用真实控件树实现规则引擎的页面查询接口。
 *
 * - 按 id、按文字查找都先交给目标 App 自己搜（快速路径，一次跨进程调用）；
 * - 按文字搜不到时（Compose 等不支持按文字搜索的界面），退而在完整遍历的结果里找。
 *   完整遍历很慢，所以同一个页面只遍历一次（lazy：第一次用到时才执行，结果缓存起来）。
 *
 * 一个实例只对应一次页面检查，用完即弃，不会拿到过期的缓存。
 */
class ServicePageQuery(private val root: AccessibilityNodeInfo) : PageQuery<AccessibilityNodeInfo> {

    private val allNodes: List<AccessibilityNodeInfo> by lazy { traverse() }

    override fun findByViewId(viewId: String): List<FoundNode<AccessibilityNodeInfo>> =
        root.findAccessibilityNodeInfosByViewId(viewId).orEmpty().map(::found)

    override fun findByText(text: String): List<FoundNode<AccessibilityNodeInfo>> {
        val fast = root.findAccessibilityNodeInfosByText(text).orEmpty()
        if (fast.isNotEmpty()) return fast.map(::found)
        return allNodes
            .filter { node ->
                node.text?.toString()?.contains(text, ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true
            }
            .map(::found)
    }

    private fun traverse(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty() && result.size < MAX_NODES) {
            val node = queue.removeFirst()
            result += node
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return result
    }

    private fun found(node: AccessibilityNodeInfo): FoundNode<AccessibilityNodeInfo> {
        val rect = Rect().also(node::getBoundsInScreen)
        return FoundNode(
            handle = node,
            info = NodeInfo(
                text = node.text?.toString(),
                contentDescription = node.contentDescription?.toString(),
                viewId = node.viewIdResourceName,
                bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
                isClickable = node.isClickable,
                isVisibleToUser = node.isVisibleToUser,
            ),
        )
    }

    private companion object {
        const val MAX_NODES = 500
    }
}

package com.nickyuan.anchor.snapshot

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * 把屏幕上的窗口和控件树转换成快照数据（SnapshotModels.kt）。
 *
 * 只读，不点击任何东西（design.md 第 5 节"快照独立"）。
 * 会访问系统的控件节点（跨进程调用），必须在后台线程调用。
 */
class SnapshotCapturer(
    /** 最多记录多少个节点，防止超大页面导出太慢、文件太大 */
    private val maxNodes: Int = DEFAULT_MAX_NODES,
    /** 控件树最多记录多少层 */
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
) {
    /**
     * @param windows 无障碍服务拿到的窗口列表（AccessibilityService.getWindows()）
     * @param fallbackRoot 窗口列表为空时（个别系统会这样），退而使用当前活动窗口的根节点
     */
    fun captureWindows(
        windows: List<AccessibilityWindowInfo>,
        fallbackRoot: AccessibilityNodeInfo?,
    ): List<WindowSnapshot> {
        val result = windows
            // 排除无障碍悬浮层，也就是我们自己的 📷 按钮
            .filter { it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
            // 按层级从上到下排，最上面的窗口最可能是广告
            .sortedByDescending { it.layer }
            .map { window ->
                val counter = NodeCounter()
                val root = window.root
                WindowSnapshot(
                    type = typeName(window.type),
                    title = window.title?.toString(),
                    packageName = root?.packageName?.toString(),
                    isActive = window.isActive,
                    layer = window.layer,
                    bounds = Rect().also(window::getBoundsInScreen).toBoundsString(),
                    root = root?.let { captureNode(it, 0, counter) },
                    truncated = counter.truncated,
                )
            }

        if (result.isNotEmpty() || fallbackRoot == null) return result

        val counter = NodeCounter()
        return listOf(
            WindowSnapshot(
                type = "active_root",
                title = null,
                packageName = fallbackRoot.packageName?.toString(),
                isActive = true,
                layer = 0,
                bounds = Rect().also(fallbackRoot::getBoundsInScreen).toBoundsString(),
                root = captureNode(fallbackRoot, 0, counter),
                truncated = counter.truncated,
            ),
        )
    }

    /** 递归记录一个节点和它的子节点 */
    private fun captureNode(node: AccessibilityNodeInfo, depth: Int, counter: NodeCounter): NodeSnapshot {
        counter.count++
        val children = mutableListOf<NodeSnapshot>()
        if (depth < maxDepth) {
            for (i in 0 until node.childCount) {
                if (counter.count >= maxNodes) {
                    counter.truncated = true
                    break
                }
                node.getChild(i)?.let { children += captureNode(it, depth + 1, counter) }
            }
        } else if (node.childCount > 0) {
            counter.truncated = true
        }

        return NodeSnapshot(
            className = node.className?.toString(),
            text = node.text?.toString(),
            desc = node.contentDescription?.toString(),
            viewId = node.viewIdResourceName,
            bounds = Rect().also(node::getBoundsInScreen).toBoundsString(),
            clickable = node.isClickable,
            visible = node.isVisibleToUser,
            enabled = node.isEnabled,
            children = children,
        )
    }

    /** 记录已经遍历了多少个节点、是否被截断 */
    private class NodeCounter(var count: Int = 0, var truncated: Boolean = false)

    private fun Rect.toBoundsString() = "$left,$top,$right,$bottom"

    private fun typeName(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "application"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "input_method"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "system"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "split_screen_divider"
        else -> "type_$type"
    }

    private companion object {
        const val DEFAULT_MAX_NODES = 3000
        const val DEFAULT_MAX_DEPTH = 60
    }
}

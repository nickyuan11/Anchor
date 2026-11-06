package com.nickyuan.anchor.engine

/**
 * 找到的一个控件：[handle] 是"原始控件"（在服务里就是系统的 AccessibilityNodeInfo，点击时要用），
 * [info] 是给规则引擎判断用的只读快照。
 *
 * <N> 是泛型参数，代表"原始控件的类型"。规则引擎不关心它具体是什么，只负责原样带回来，
 * 这样引擎就不依赖 Android，单元测试里可以用字符串之类的假控件代替。
 */
data class FoundNode<N>(val handle: N, val info: NodeInfo)

/**
 * 规则引擎查询页面的接口。服务里用真实的控件树实现，单元测试里用假数据实现。
 * 类似 Java 里面向接口编程，方便替换实现做测试（像 Mock 一个 DAO）。
 */
interface PageQuery<N> {
    /** 按 view id 精确查找 */
    fun findByViewId(viewId: String): List<FoundNode<N>>

    /** 查找 text 或内容描述中包含 [text] 的控件（不区分大小写） */
    fun findByText(text: String): List<FoundNode<N>>
}

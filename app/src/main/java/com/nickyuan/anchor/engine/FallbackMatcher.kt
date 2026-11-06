package com.nickyuan.anchor.engine

/**
 * 通用关键词兜底的参数（design.md 第 2 节"通用兜底"）。
 * M4 会把它们做成设置项，M2 先用默认值。
 */
data class FallbackConfig(
    /** 关键词：控件文字里包含其中之一才考虑点击（英文不区分大小写） */
    val keywords: List<String> = listOf("跳过", "跳过广告", "Skip"),
    /** 文字最多几个字（去掉空格后）。排除"已跳过 3 个广告"这类长句子 */
    val maxTextLength: Int = 10,
    /** "角落"的横向范围：控件中心离左边或右边不超过屏幕宽度的这个比例 */
    val cornerXRatio: Float = 0.35f,
    /** "角落"的纵向范围：控件中心离顶部或底部不超过屏幕高度的这个比例 */
    val cornerYRatio: Float = 0.20f,
    /** 控件最大宽度占屏幕宽度的比例。跳过按钮都很小 */
    val maxWidthRatio: Float = 0.40f,
    /** 控件最大高度占屏幕高度的比例 */
    val maxHeightRatio: Float = 0.15f,
)

/**
 * 通用关键词兜底：在没有专属规则时，从候选控件里找出最像"跳过"按钮的那一个。
 *
 * 为了减少误点，一个控件必须同时满足：
 * 1. 文字包含关键词，并且文字很短；
 * 2. 用户看得见；
 * 3. 位于屏幕四个角附近；
 * 4. 尺寸较小。
 * "只在 App 刚打开的几秒内生效"这一条由 LaunchTracker 负责，不在这里判断。
 */
class FallbackMatcher(private val config: FallbackConfig = FallbackConfig()) {

    /** 匹配结果：要点击的控件、命中的关键词、控件上的原始文字 */
    data class Match(val node: NodeInfo, val keyword: String, val matchedText: String)

    /** 关键词列表，给 NodeFinder 做初筛用 */
    val keywords: List<String> get() = config.keywords

    /**
     * @param candidates 候选控件（通常是 NodeFinder 初筛出的、文字含关键词的控件）
     * @param screenWidth、screenHeight 屏幕尺寸，单位像素
     * @return 最合适的一个；都不满足条件时返回 null
     */
    fun findTarget(candidates: List<NodeInfo>, screenWidth: Int, screenHeight: Int): Match? {
        if (screenWidth <= 0 || screenHeight <= 0) return null

        val matches = candidates.mapNotNull { node ->
            val label = node.label ?: return@mapNotNull null
            val keyword = matchKeyword(label) ?: return@mapNotNull null
            if (!node.isVisibleToUser || node.bounds.isEmpty) return@mapNotNull null
            if (!isInCorner(node.bounds, screenWidth, screenHeight)) return@mapNotNull null
            if (!isSmall(node.bounds, screenWidth, screenHeight)) return@mapNotNull null
            Match(node, keyword, label)
        }

        // 有多个时：优先选本身可点击的，再选面积最小的（越小越像按钮，越不像正文）
        return matches.minWithOrNull(
            compareBy<Match>({ !it.node.isClickable }, { it.node.bounds.width.toLong() * it.node.bounds.height }),
        )
    }

    /** 返回命中的关键词；文字太长或不含关键词时返回 null */
    private fun matchKeyword(label: String): String? {
        // 去掉所有空白字符，"跳过 5" → "跳过5"
        val normalized = label.filterNot { it.isWhitespace() }
        if (normalized.length > config.maxTextLength) return null
        return config.keywords.firstOrNull { normalized.contains(it, ignoreCase = true) }
    }

    private fun isInCorner(b: Bounds, w: Int, h: Int): Boolean {
        val nearLeftOrRight = b.centerX <= w * config.cornerXRatio || b.centerX >= w * (1 - config.cornerXRatio)
        val nearTopOrBottom = b.centerY <= h * config.cornerYRatio || b.centerY >= h * (1 - config.cornerYRatio)
        return nearLeftOrRight && nearTopOrBottom
    }

    private fun isSmall(b: Bounds, w: Int, h: Int): Boolean =
        b.width <= w * config.maxWidthRatio && b.height <= h * config.maxHeightRatio
}

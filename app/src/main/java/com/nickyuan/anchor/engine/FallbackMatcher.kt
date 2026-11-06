package com.nickyuan.anchor.engine

/**
 * 通用关键词兜底的参数（design.md 第 2 节"通用兜底"）。
 * M4 会把它们做成设置项，M2 先用默认值。
 */
data class FallbackConfig(
    /** 关键词：控件文字里包含其中之一才考虑点击（英文不区分大小写） */
    val keywords: List<String> = listOf("跳过", "跳过广告", "Skip"),
    /**
     * 有条件的关键词：太常见、容易误点的词（例如"关闭"），
     * 只有页面上同时出现了 [adMarkers] 里的广告特征文字时才生效。
     * 来源：爱奇艺开屏广告的按钮写的是"关闭"（M3 真机快照）。
     */
    val conditionalKeywords: List<String> = listOf("关闭"),
    /** 广告特征文字：页面上出现其中之一，才允许使用 [conditionalKeywords] */
    val adMarkers: List<String> = listOf("广告", "跳转至第三方应用", "跳转第三方应用") + SHAKE_KEYWORDS,
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
) {
    companion object {
        /**
         * "摇一摇"类文字：出现时记入日志（design.md 第 2 节"识别记录"）。
         * 扭转、翻转手机来自腾讯视频、爱奇艺的真机快照，原理同摇一摇，只是用的是陀螺仪。
         */
        val SHAKE_KEYWORDS = listOf("摇一摇", "摇动手机", "扭动手机", "扭转手机", "翻转手机", "转动手机", "扭一扭")
    }
}

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

    /** 所有关键词（含有条件的），给 NodeFinder 做初筛用 */
    val allKeywords: List<String> get() = config.keywords + config.conditionalKeywords

    /** 广告特征文字 */
    val adMarkers: List<String> get() = config.adMarkers

    /** 候选里是否有"只靠有条件关键词命中"的控件——有的话才值得去检查广告特征，省一次查询 */
    fun hasConditionalCandidate(candidates: List<NodeInfo>): Boolean = candidates.any { node ->
        val label = node.label ?: return@any false
        matchKeyword(label, config.keywords) == null && matchKeyword(label, config.conditionalKeywords) != null
    }

    /**
     * @param candidates 候选控件（通常是 NodeFinder 初筛出的、文字含关键词的控件）
     * @param screenWidth、screenHeight 屏幕尺寸，单位像素
     * @param adMarkersPresent 页面上是否有广告特征文字；为 true 时才允许"关闭"等有条件关键词
     * @return 最合适的一个；都不满足条件时返回 null
     */
    fun findTarget(
        candidates: List<NodeInfo>,
        screenWidth: Int,
        screenHeight: Int,
        adMarkersPresent: Boolean = false,
    ): Match? {
        if (screenWidth <= 0 || screenHeight <= 0) return null

        val matches = candidates.mapNotNull { node ->
            val label = node.label ?: return@mapNotNull null
            val keyword = matchKeyword(label, config.keywords)
                ?: (if (adMarkersPresent) matchKeyword(label, config.conditionalKeywords) else null)
                ?: return@mapNotNull null
            if (!node.isVisibleToUser || node.bounds.isEmpty) return@mapNotNull null
            if (!isInCorner(node.bounds, screenWidth, screenHeight)) return@mapNotNull null
            if (!isSmall(node.bounds, screenWidth, screenHeight)) return@mapNotNull null
            Match(node, keyword, label)
        }

        // 有多个时依次比较：
        // 1. "跳过"这类明确的关键词优先于"关闭"这类有条件的；
        // 2. 本身可点击的优先；
        // 3. 面积小的优先（越小越像按钮，越不像正文）
        return matches.minWithOrNull(
            compareBy<Match>(
                { it.keyword in config.conditionalKeywords },
                { !it.node.isClickable },
                { it.node.bounds.width.toLong() * it.node.bounds.height },
            ),
        )
    }

    /** 返回命中的关键词；文字太长或不含关键词时返回 null */
    private fun matchKeyword(label: String, words: List<String>): String? {
        // 去掉所有空白字符，"跳过 5" → "跳过5"
        val normalized = label.filterNot { it.isWhitespace() }
        if (normalized.length > config.maxTextLength) return null
        return words.firstOrNull { normalized.contains(it, ignoreCase = true) }
    }

    private fun isInCorner(b: Bounds, w: Int, h: Int): Boolean {
        val nearLeftOrRight = b.centerX <= w * config.cornerXRatio || b.centerX >= w * (1 - config.cornerXRatio)
        val nearTopOrBottom = b.centerY <= h * config.cornerYRatio || b.centerY >= h * (1 - config.cornerYRatio)
        return nearLeftOrRight && nearTopOrBottom
    }

    private fun isSmall(b: Bounds, w: Int, h: Int): Boolean =
        b.width <= w * config.maxWidthRatio && b.height <= h * config.maxHeightRatio
}

package com.nickyuan.anchor.engine

import com.nickyuan.anchor.rule.model.AppRules
import com.nickyuan.anchor.rule.model.MatchCondition
import com.nickyuan.anchor.rule.model.Rule
import com.nickyuan.anchor.rule.model.RuleAction
import com.nickyuan.anchor.rule.model.RuleType
import com.nickyuan.anchor.rule.model.TextMatch

/** 规则匹配的结果：要执行什么动作 */
sealed interface RuleDecision<N> {
    val rule: Rule

    /** 点击一个控件。[matchedBy] 是命中的条件说明，记进跳过记录 */
    data class ClickNode<N>(override val rule: Rule, val target: FoundNode<N>, val matchedBy: String) : RuleDecision<N>

    /** 点击屏幕坐标（像素） */
    data class TapPosition<N>(override val rule: Rule, val x: Int, val y: Int) : RuleDecision<N>

    /** 按返回键 */
    data class Back<N>(override val rule: Rule) : RuleDecision<N>
}

/**
 * 规则引擎（design.md 第 5、6 节）：根据"包名、Activity、页面内容"决定执行哪条规则。
 * 不依赖 Android，可以脱离手机写单元测试。
 */
class RuleMatcher {

    /**
     * 按顺序检查这个 App 的每条规则，返回第一条满足所有条件的规则对应的动作；都不满足返回 null。
     *
     * @param activity 当前页面类名，未知时为 null（此时只有 activities 为 "*" 的规则能生效）
     * @param elapsedSinceLaunchMs App 打开后过了多少毫秒
     * @param clicksSoFar 某条规则在本次打开中已经执行过几次
     */
    fun <N> match(
        app: AppRules,
        activity: String?,
        elapsedSinceLaunchMs: Long,
        page: PageQuery<N>,
        screenWidth: Int,
        screenHeight: Int,
        clicksSoFar: (ruleId: String) -> Int,
    ): RuleDecision<N>? {
        if (!app.enabled) return null
        for (rule in app.rules) {
            if (rule.type != RuleType.SPLASH) continue // 弹窗规则 v1.2 才支持
            if (!activityMatches(rule, activity)) continue
            if (elapsedSinceLaunchMs > rule.matchWindowMs) continue
            if (clicksSoFar(rule.id) >= rule.maxClicks) continue
            if (!conditionMet(rule, page)) continue

            val decision: RuleDecision<N>? = when (rule.action) {
                RuleAction.CLICK -> findTarget(rule, page)
                RuleAction.TAP_POSITION -> rule.position?.let {
                    RuleDecision.TapPosition(rule, (it.x * screenWidth).toInt(), (it.y * screenHeight).toInt())
                }
                RuleAction.BACK -> RuleDecision.Back(rule)
            }
            if (decision != null) return decision
        }
        return null
    }

    /** 这个 App 所有规则里最长的生效时间，服务用它决定"打开后多久之内还需要看页面" */
    fun maxWindowMs(app: AppRules): Long =
        if (!app.enabled) 0 else app.rules.filter { it.type == RuleType.SPLASH }.maxOfOrNull { it.matchWindowMs } ?: 0

    private fun activityMatches(rule: Rule, activity: String?): Boolean =
        Rule.ALL_ACTIVITIES in rule.activities || (activity != null && activity in rule.activities)

    private fun <N> conditionMet(rule: Rule, page: PageQuery<N>): Boolean {
        val text = rule.condition?.textExists ?: return true
        return page.findByText(text).any { it.info.isVisibleToUser }
    }

    /** 按 match 的顺序逐个尝试，返回第一个命中的控件 */
    private fun <N> findTarget(rule: Rule, page: PageQuery<N>): RuleDecision.ClickNode<N>? {
        for (condition in rule.match) {
            val found = query(condition, page)
                .filter { it.info.isVisibleToUser && !it.info.bounds.isEmpty && matches(condition, it.info) }
                // 多个时优先选本身可点击的，再选面积小的
                .minWithOrNull(compareBy({ !it.info.isClickable }, { it.info.bounds.width.toLong() * it.info.bounds.height }))
            if (found != null) return RuleDecision.ClickNode(rule, found, describe(condition))
        }
        return null
    }

    /** 先用最快的方式查出候选：有 viewId 就按 id 查，否则按文字查 */
    private fun <N> query(condition: MatchCondition, page: PageQuery<N>): List<FoundNode<N>> = when {
        condition.viewId != null -> page.findByViewId(condition.viewId)
        // 正则不能直接交给系统搜索，只能先取出所有带文字的控件再筛；这里退而用正则里的普通字符片段做初筛
        condition.text != null -> page.findByText(searchHint(condition.text, condition.textMatch))
        condition.desc != null -> page.findByText(searchHint(condition.desc, condition.textMatch))
        else -> emptyList()
    }

    /** 检查控件是否满足条件里写了的每一个字段 */
    private fun matches(condition: MatchCondition, node: NodeInfo): Boolean {
        if (condition.viewId != null && node.viewId != condition.viewId) return false
        if (condition.text != null && !textMatches(node.text, condition.text, condition.textMatch)) return false
        if (condition.desc != null && !textMatches(node.contentDescription, condition.desc, condition.textMatch)) return false
        return true
    }

    private fun textMatches(actual: String?, expected: String, mode: TextMatch): Boolean {
        if (actual == null) return false
        return when (mode) {
            // 比较前去掉首尾空白，"跳过 " 和 "跳过" 视为相同
            TextMatch.EQUALS -> actual.trim() == expected
            TextMatch.CONTAINS -> actual.contains(expected, ignoreCase = true)
            TextMatch.REGEX -> runCatching { Regex(expected).containsMatchIn(actual) }.getOrDefault(false)
        }
    }

    /**
     * 正则模式下，取正则里最长的一段普通文字作为系统搜索的关键词，例如 "跳过\\s*\\d+" → "跳过"。
     * 系统搜索只支持"包含"，先用它缩小范围，再用完整正则精确判断。
     */
    private fun searchHint(pattern: String, mode: TextMatch): String {
        if (mode != TextMatch.REGEX) return pattern
        return pattern.split(Regex("""[\\.^$|?*+()\[\]{}]""")).maxByOrNull { it.length }.orEmpty()
            .ifEmpty { pattern }
    }

    private fun describe(c: MatchCondition): String = listOfNotNull(
        c.viewId?.let { "viewId=$it" },
        c.text?.let { "text=$it" },
        c.desc?.let { "desc=$it" },
    ).joinToString(" ")
}

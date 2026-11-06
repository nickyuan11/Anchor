package com.nickyuan.anchor.engine

import com.nickyuan.anchor.rule.model.AppRules
import com.nickyuan.anchor.rule.model.Condition
import com.nickyuan.anchor.rule.model.MatchCondition
import com.nickyuan.anchor.rule.model.Position
import com.nickyuan.anchor.rule.model.Rule
import com.nickyuan.anchor.rule.model.RuleAction
import com.nickyuan.anchor.rule.model.RuleType
import com.nickyuan.anchor.rule.model.TextMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    /** 假页面：控件用名字（字符串）当"原始控件" */
    private class FakePage(vararg nodes: Pair<String, NodeInfo>) : PageQuery<String> {
        val all = nodes.map { FoundNode(it.first, it.second) }
        override fun findByViewId(viewId: String) = all.filter { it.info.viewId == viewId }
        override fun findByText(text: String) = all.filter {
            it.info.text?.contains(text, true) == true || it.info.contentDescription?.contains(text, true) == true
        }
    }

    private fun node(text: String? = null, viewId: String? = null, desc: String? = null, visible: Boolean = true, clickable: Boolean = true) =
        NodeInfo(text, desc, viewId, Bounds(900, 100, 1050, 170), clickable, visible)

    private val matcher = RuleMatcher()
    private val skip = MatchCondition(text = "跳过")

    private fun app(vararg rules: Rule, enabled: Boolean = true) = AppRules("com.a", rules = rules.toList(), enabled = enabled)

    private fun match(
        app: AppRules,
        page: FakePage,
        activity: String? = "com.a.Splash",
        elapsed: Long = 1_000,
        clicks: Int = 0,
    ) = matcher.match(app, activity, elapsed, page, 1000, 2000) { clicks }

    @Test
    fun `按文字匹配到跳过按钮`() {
        val d = match(app(Rule("r", match = listOf(skip))), FakePage("btn" to node("跳过 5")))
        assertTrue(d is RuleDecision.ClickNode)
        assertEquals("btn", (d as RuleDecision.ClickNode).target.handle)
        assertEquals("r", d.rule.id)
    }

    @Test
    fun `match 按顺序尝试，前面的没命中再试后面的`() {
        val rule = Rule("r", match = listOf(MatchCondition(viewId = "com.a:id/skip"), skip))
        val d = match(app(rule), FakePage("text" to node("跳过")))
        assertEquals("text", (d as RuleDecision.ClickNode).target.handle)
        assertEquals("text=跳过", d.matchedBy)

        val both = FakePage("text" to node("跳过"), "id" to node(viewId = "com.a:id/skip"))
        assertEquals("id", (match(app(rule), both) as RuleDecision.ClickNode).target.handle)
    }

    @Test
    fun `同一条件里写了多个字段时必须全部满足`() {
        val rule = Rule("r", match = listOf(MatchCondition(viewId = "com.a:id/skip", text = "跳过")))
        assertNull(match(app(rule), FakePage("x" to node("关闭", viewId = "com.a:id/skip"))))
        assertTrue(match(app(rule), FakePage("x" to node("跳过", viewId = "com.a:id/skip"))) is RuleDecision.ClickNode)
    }

    @Test
    fun `三种文字匹配方式`() {
        val equals = Rule("e", match = listOf(MatchCondition(text = "关闭", textMatch = TextMatch.EQUALS)))
        assertTrue(match(app(equals), FakePage("x" to node(" 关闭 "))) is RuleDecision.ClickNode)
        assertNull(match(app(equals), FakePage("x" to node("关闭广告"))))

        val regex = Rule("g", match = listOf(MatchCondition(text = "^跳过\\s*\\d+$", textMatch = TextMatch.REGEX)))
        assertTrue(match(app(regex), FakePage("x" to node("跳过 5"))) is RuleDecision.ClickNode)
        assertNull(match(app(regex), FakePage("x" to node("跳过广告"))))
    }

    @Test
    fun `按内容描述匹配`() {
        val rule = Rule("r", match = listOf(MatchCondition(desc = "跳过广告")))
        assertTrue(match(app(rule), FakePage("img" to node(desc = "跳过广告"))) is RuleDecision.ClickNode)
    }

    @Test
    fun `页面不对不生效，星号匹配所有页面包括未知页面`() {
        val specific = Rule("r", activities = listOf("com.a.Splash"), match = listOf(skip))
        val page = FakePage("x" to node("跳过"))
        assertTrue(match(app(specific), page, activity = "com.a.Splash") is RuleDecision.ClickNode)
        assertNull(match(app(specific), page, activity = "com.a.Main"))
        assertNull(match(app(specific), page, activity = null))
        assertTrue(match(app(Rule("s", match = listOf(skip))), page, activity = null) is RuleDecision.ClickNode)
    }

    @Test
    fun `超过生效时间不生效`() {
        val rule = Rule("r", matchWindowMs = 8_000, match = listOf(skip))
        val page = FakePage("x" to node("跳过"))
        assertTrue(match(app(rule), page, elapsed = 8_000) is RuleDecision.ClickNode)
        assertNull(match(app(rule), page, elapsed = 8_001))
    }

    @Test
    fun `达到 maxClicks 后不再生效`() {
        val rule = Rule("r", match = listOf(skip), maxClicks = 2)
        val page = FakePage("x" to node("跳过"))
        assertTrue(match(app(rule), page, clicks = 1) is RuleDecision.ClickNode)
        assertNull(match(app(rule), page, clicks = 2))
    }

    @Test
    fun `前置条件不满足不生效`() {
        val rule = Rule("r", condition = Condition("第三方应用"), match = listOf(MatchCondition(text = "关闭")))
        assertNull(match(app(rule), FakePage("x" to node("关闭"))))
        assertTrue(match(app(rule), FakePage("x" to node("关闭"), "y" to node("跳转至第三方应用"))) is RuleDecision.ClickNode)
        // 前置条件的文字不可见也不算
        assertNull(match(app(rule), FakePage("x" to node("关闭"), "y" to node("跳转至第三方应用", visible = false))))
    }

    @Test
    fun `看不见的控件不点，多个时优先可点击的`() {
        val rule = Rule("r", match = listOf(skip))
        assertNull(match(app(rule), FakePage("x" to node("跳过", visible = false))))
        val d = match(app(rule), FakePage("text" to node("跳过", clickable = false), "btn" to node("跳过")))
        assertEquals("btn", (d as RuleDecision.ClickNode).target.handle)
    }

    @Test
    fun `点击坐标按屏幕百分比换算成像素`() {
        val rule = Rule("p", action = RuleAction.TAP_POSITION, position = Position(0.88f, 0.06f), condition = Condition("摇一摇"))
        val d = match(app(rule), FakePage("x" to node("摇一摇")))
        assertEquals(RuleDecision.TapPosition<String>(rule, 880, 120), d)
    }

    @Test
    fun `返回键动作`() {
        val rule = Rule("b", action = RuleAction.BACK, condition = Condition("广告"))
        assertTrue(match(app(rule), FakePage("x" to node("广告"))) is RuleDecision.Back)
    }

    @Test
    fun `App 停用或弹窗类规则不生效`() {
        val page = FakePage("x" to node("跳过"))
        assertNull(match(app(Rule("r", match = listOf(skip)), enabled = false), page))
        assertNull(match(app(Rule("r", type = RuleType.POPUP, match = listOf(skip))), page))
    }

    @Test
    fun `前一条规则没命中时试下一条`() {
        val first = Rule("first", match = listOf(MatchCondition(text = "关闭")))
        val second = Rule("second", match = listOf(skip))
        assertEquals("second", match(app(first, second), FakePage("x" to node("跳过")))!!.rule.id)
    }

    @Test
    fun `最长生效时间`() {
        assertEquals(8_000, matcher.maxWindowMs(app(Rule("a", matchWindowMs = 3_000, match = listOf(skip)), Rule("b", matchWindowMs = 8_000, match = listOf(skip)))))
        assertEquals(0, matcher.maxWindowMs(app(Rule("a", match = listOf(skip)), enabled = false)))
    }

    @Test
    fun `每次打开重新计数`() {
        val counter = PerLaunchCounter()
        counter.syncLaunch(1)
        counter.increment("r")
        counter.syncLaunch(1)
        assertEquals(1, counter.count("r"))
        counter.syncLaunch(2)
        assertEquals(0, counter.count("r"))
    }
}

package com.nickyuan.anchor.rule

import com.nickyuan.anchor.rule.model.AppRules
import com.nickyuan.anchor.rule.model.ClickBy
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
import java.io.File

class RuleParserTest {

    /** design.md 第 6 节的示例，原样复制 */
    private val designExample = """
        {
          "version": 1,
          "apps": [
            {
              "packageName": "com.example.app",
              "name": "示例 App",
              "enabled": true,
              "rules": [
                {
                  "id": "splash-skip",
                  "type": "splash",
                  "activities": ["com.example.app.SplashActivity"],
                  "matchWindowMs": 8000,
                  "match": [
                    { "viewId": "com.example.app:id/skip_btn" },
                    { "text": "跳过", "textMatch": "contains" }
                  ],
                  "action": "click",
                  "clickBy": "gesture",
                  "maxClicks": 1
                },
                {
                  "id": "splash-skip-by-position",
                  "type": "splash",
                  "activities": ["*"],
                  "condition": { "textExists": "摇一摇" },
                  "position": { "x": 0.88, "y": 0.06 },
                  "action": "tapPosition",
                  "maxClicks": 1
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `能解析设计文档里的示例`() {
        val file = RuleParser.parse(designExample)
        val rules = file.apps.single().rules
        assertEquals(2, rules.size)

        val first = rules[0]
        assertEquals("splash-skip", first.id)
        assertEquals(RuleType.SPLASH, first.type)
        assertEquals(8000, first.matchWindowMs)
        assertEquals(MatchCondition(viewId = "com.example.app:id/skip_btn"), first.match[0])
        assertEquals(MatchCondition(text = "跳过", textMatch = TextMatch.CONTAINS), first.match[1])
        assertEquals(ClickBy.GESTURE, first.clickBy)

        val second = rules[1]
        assertEquals(RuleAction.TAP_POSITION, second.action)
        assertEquals(Position(0.88f, 0.06f), second.position)
        assertEquals(Condition(textExists = "摇一摇"), second.condition)
        assertNull(second.clickBy)

        assertTrue(rules.all { RuleParser.validate(it).isEmpty() })
    }

    @Test
    fun `不写的字段取默认值，不认识的字段忽略`() {
        val file = RuleParser.parse(
            """{"version":1,"apps":[{"packageName":"a","futureField":1,"rules":[{"id":"r","match":[{"text":"跳过"}]}]}]}""",
        )
        val rule = file.apps.single().rules.single()
        assertEquals(listOf("*"), rule.activities)
        assertEquals(Rule.DEFAULT_MATCH_WINDOW_MS, rule.matchWindowMs)
        assertEquals(RuleAction.CLICK, rule.action)
        assertEquals(TextMatch.CONTAINS, rule.match.single().textMatch)
        assertEquals(1, rule.maxClicks)
    }

    @Test
    fun `校验能发现常见错误`() {
        fun errors(rule: Rule) = RuleParser.validate(rule)
        assertTrue(errors(Rule(id = "a")).any { "必须写 match" in it })
        assertTrue(errors(Rule(id = "a", action = RuleAction.TAP_POSITION, condition = Condition("广告"))).any { "position" in it })
        assertTrue(
            errors(Rule(id = "a", action = RuleAction.TAP_POSITION, position = Position(0.5f, 0.5f)))
                .any { "condition" in it },
        )
        assertTrue(
            errors(Rule(id = "a", action = RuleAction.TAP_POSITION, position = Position(1.5f, 0.5f), condition = Condition("x")))
                .any { "0～1" in it },
        )
        assertTrue(errors(Rule(id = "a", action = RuleAction.BACK)).any { "condition" in it })
        assertTrue(errors(Rule(id = "a", match = listOf(MatchCondition()))).any { "至少要写" in it })
        assertTrue(
            errors(Rule(id = "a", match = listOf(MatchCondition(text = "跳过(", textMatch = TextMatch.REGEX))))
                .any { "正则" in it },
        )
        assertTrue(errors(Rule(id = "a", match = listOf(MatchCondition(text = "跳过")), maxClicks = 0)).any { "maxClicks" in it })
    }

    @Test
    fun `不合法的规则被丢弃，同文件其他规则保留`() {
        val file = RuleParser.parse(
            """{"version":1,"apps":[{"packageName":"a","rules":[{"id":"bad"},{"id":"good","match":[{"text":"跳过"}]}]}]}""",
        )
        val (apps, problems) = RuleParser.sanitize(file)
        assertEquals(listOf("good"), apps.single().rules.map { it.id })
        assertEquals(1, problems.size)
    }

    @Test
    fun `用户规则按 id 覆盖内置规则，新规则追加在后面`() {
        val skip = MatchCondition(text = "跳过")
        val builtin = listOf(
            AppRules("a", "A", rules = listOf(Rule("r1", match = listOf(skip)), Rule("r2", match = listOf(skip)))),
            AppRules("b", "B", rules = listOf(Rule("r1", match = listOf(skip)))),
        )
        val user = listOf(
            AppRules("a", enabled = false, rules = listOf(Rule("r2", match = listOf(skip), maxClicks = 3), Rule("r3", match = listOf(skip)))),
            AppRules("c", "C", rules = listOf(Rule("x", match = listOf(skip)))),
        )
        val merged = RuleParser.merge(builtin, user).associateBy { it.packageName }

        val a = merged.getValue("a")
        assertEquals("A", a.name) // 用户没写 name，保留内置的
        assertEquals(false, a.enabled)
        assertEquals(listOf("r1", "r2", "r3"), a.rules.map { it.id })
        assertEquals(3, a.rules[1].maxClicks)
        assertEquals(listOf("r1"), merged.getValue("b").rules.map { it.id })
        assertEquals(listOf("x"), merged.getValue("c").rules.map { it.id })
    }

    @Test
    fun `内置规则和 debug 规则文件格式正确、每条规则都合法`() {
        // 单元测试的工作目录是 app 模块目录
        for (path in listOf("src/main/assets/rules/builtin.json", "src/debug/assets/rules/debug.json")) {
            val (apps, problems) = RuleParser.sanitize(RuleParser.parse(File(path).readText()))
            assertTrue("$path：$problems", problems.isEmpty())
            assertTrue(path, apps.isNotEmpty() && apps.all { it.rules.isNotEmpty() })
            val ids = apps.flatMap { app -> app.rules.map { "${app.packageName}/${it.id}" } }
            assertEquals("$path 有重复的规则 id", ids.size, ids.toSet().size)
        }
    }
}

package com.nickyuan.anchor.rule

import com.nickyuan.anchor.rule.model.AppRules
import com.nickyuan.anchor.rule.model.Rule
import com.nickyuan.anchor.rule.model.RuleAction
import com.nickyuan.anchor.rule.model.RuleFile
import com.nickyuan.anchor.rule.model.TextMatch
import kotlinx.serialization.json.Json

/**
 * 规则文件的解析、校验与合并。纯 Kotlin，可以直接单元测试。
 */
object RuleParser {

    /**
     * ignoreUnknownKeys：JSON 里有不认识的字段时忽略，而不是报错。
     * 这样以后给规则格式加新字段，旧版本 App 读新规则文件也不会崩。
     */
    private val json = Json { ignoreUnknownKeys = true }

    /** 解析失败（JSON 写错了）会抛出异常 */
    fun parse(text: String): RuleFile = json.decodeFromString(RuleFile.serializer(), text)

    /**
     * 检查一条规则是否合法，返回所有问题的描述；没问题返回空列表。
     * 不合法的规则在加载时会被跳过，不影响同一个文件里的其他规则。
     */
    fun validate(rule: Rule): List<String> = buildList {
        if (rule.id.isBlank()) add("id 不能为空")
        if (rule.activities.isEmpty()) add("activities 不能为空，所有页面请写 [\"*\"]")
        if (rule.matchWindowMs <= 0) add("matchWindowMs 必须大于 0")
        if (rule.maxClicks < 1) add("maxClicks 至少为 1")

        for (m in rule.match) {
            if (m.viewId == null && m.text == null && m.desc == null) add("match 里每一项至少要写 viewId、text、desc 之一")
            if (m.textMatch == TextMatch.REGEX) {
                listOfNotNull(m.text, m.desc).forEach { pattern ->
                    if (runCatching { Regex(pattern) }.isFailure) add("正则表达式写错了：$pattern")
                }
            }
        }

        when (rule.action) {
            RuleAction.CLICK -> if (rule.match.isEmpty()) add("action 为 click 时必须写 match")
            RuleAction.TAP_POSITION -> {
                val p = rule.position
                if (p == null) {
                    add("action 为 tapPosition 时必须写 position")
                } else if (p.x !in 0f..1f || p.y !in 0f..1f) {
                    add("position 的 x、y 必须在 0～1 之间")
                }
                // 盲点坐标风险高：必须有前置条件，确认确实是广告页面才点
                if (rule.condition?.textExists == null) add("action 为 tapPosition 时必须写 condition，确认是广告页面再点")
            }
            RuleAction.BACK ->
                if (rule.condition?.textExists == null) add("action 为 back 时必须写 condition，确认是广告页面再按返回")
        }
    }

    /** 去掉不合法的规则；返回保留下来的规则和被丢弃规则的说明 */
    fun sanitize(file: RuleFile): Pair<List<AppRules>, List<String>> {
        val problems = mutableListOf<String>()
        val apps = file.apps.map { app ->
            app.copy(
                rules = app.rules.filter { rule ->
                    val errors = validate(rule)
                    errors.forEach { problems += "${app.packageName} / ${rule.id}：$it" }
                    errors.isEmpty()
                },
            )
        }
        return apps to problems
    }

    /**
     * 合并内置规则和用户规则（design.md 第 6 节："用户规则按 id 覆盖同名内置规则"）。
     * - 同一个 App：用户写了 enabled / name 就以用户的为准；
     * - 同一个 App 下 id 相同的规则：用用户的；用户新增的规则排在后面。
     */
    fun merge(builtin: List<AppRules>, user: List<AppRules>): List<AppRules> {
        val result = LinkedHashMap<String, AppRules>()
        for (app in builtin) result[app.packageName] = app
        for (userApp in user) {
            val base = result[userApp.packageName]
            if (base == null) {
                result[userApp.packageName] = userApp
                continue
            }
            val rules = LinkedHashMap<String, Rule>()
            base.rules.forEach { rules[it.id] = it }
            userApp.rules.forEach { rules[it.id] = it }
            result[userApp.packageName] = base.copy(
                name = userApp.name ?: base.name,
                enabled = userApp.enabled,
                rules = rules.values.toList(),
            )
        }
        return result.values.toList()
    }
}

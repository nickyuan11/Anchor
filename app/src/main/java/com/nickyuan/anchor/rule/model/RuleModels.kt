package com.nickyuan.anchor.rule.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * 规则文件的数据结构，严格对应 design.md 第 6 节。
 * 内置规则（assets/rules/builtin.json）和用户规则格式相同。
 *
 * @SerialName：JSON 里的名字和 Kotlin 里的名字不一样时，用它指定 JSON 里的写法。
 * 字段的默认值 = JSON 里不写这个字段时的取值。
 */

/** 一个规则文件 */
@Serializable
data class RuleFile(
    val version: Int,
    val apps: List<AppRules>,
)

/** 一个 App 的所有规则 */
@Serializable
data class AppRules(
    val packageName: String,
    val name: String? = null,
    /** 整个 App 的规则开关 */
    val enabled: Boolean = true,
    val rules: List<Rule> = emptyList(),
)

/** 一条规则 */
@Serializable
data class Rule(
    /** 规则唯一标识，用户规则以此覆盖同名内置规则 */
    val id: String,
    val type: RuleType = RuleType.SPLASH,
    /** 生效的页面类名，"*" 表示该 App 所有页面 */
    val activities: List<String> = listOf(ALL_ACTIVITIES),
    /** App 进入前台后多少毫秒内有效 */
    val matchWindowMs: Long = DEFAULT_MATCH_WINDOW_MS,
    /** 节点匹配条件，按顺序尝试，命中一个即可 */
    val match: List<MatchCondition> = emptyList(),
    /** 可选的前置条件 */
    val condition: Condition? = null,
    /** 按屏幕百分比坐标点击（action = tapPosition 时使用） */
    val position: Position? = null,
    val action: RuleAction = RuleAction.CLICK,
    /** 点击方式，仅 action = click 时有效；不写时先点控件，无效再用手势重试 */
    val clickBy: ClickBy? = null,
    /** 每次进入页面最多执行几次 */
    val maxClicks: Int = 1,
) {
    companion object {
        const val ALL_ACTIVITIES = "*"
        const val DEFAULT_MATCH_WINDOW_MS = 5_000L
    }
}

@Serializable
enum class RuleType {
    /** 开屏广告 */
    @SerialName("splash") SPLASH,

    /** 弹窗（v1.2 才支持，目前读到会跳过） */
    @SerialName("popup") POPUP,
}

/**
 * 一个节点匹配条件。写了的字段必须全部满足（例如同时写 viewId 和 text，表示 id 和文字都要对上）。
 */
@Serializable
data class MatchCondition(
    /** view id，例如 "com.example.app:id/skip_btn" */
    val viewId: String? = null,
    /** 控件文字 */
    val text: String? = null,
    /** 文字匹配方式，同时作用于 text 和 desc */
    val textMatch: TextMatch = TextMatch.CONTAINS,
    /** 内容描述（content-description） */
    val desc: String? = null,
)

@Serializable
enum class TextMatch {
    @SerialName("equals") EQUALS,
    @SerialName("contains") CONTAINS,
    @SerialName("regex") REGEX,
}

/** 前置条件 */
@Serializable
data class Condition(
    /** 页面上必须存在包含这段文字的控件 */
    val textExists: String? = null,
)

/** 屏幕百分比坐标，0.0～1.0，原点在左上角 */
@Serializable
data class Position(val x: Float, val y: Float)

@Serializable
enum class RuleAction {
    /** 点击匹配到的节点 */
    @SerialName("click") CLICK,

    /** 点击 position 指定的屏幕坐标 */
    @SerialName("tapPosition") TAP_POSITION,

    /** 按返回键 */
    @SerialName("back") BACK,
}

@Serializable
enum class ClickBy {
    /** 让系统直接点控件（performAction） */
    @SerialName("node") NODE,

    /** 在控件位置模拟手指点击（dispatchGesture） */
    @SerialName("gesture") GESTURE,
}

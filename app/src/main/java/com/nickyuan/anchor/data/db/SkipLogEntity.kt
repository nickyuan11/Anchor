package com.nickyuan.anchor.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条跳过记录，对应数据库表 skip_log。
 * @Entity 相当于 JPA / MyBatis-Plus 里的 @Table 实体类，Room 会根据它建表。
 */
@Entity(tableName = "skip_log")
data class SkipLogEntity(
    /** 主键，autoGenerate = 自增 */
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 发生时间，毫秒时间戳 */
    val timestamp: Long,
    /** 目标 App 包名 */
    val packageName: String,
    /** 页面（Activity）完整类名，未知时为 null */
    val activityName: String?,
    /** 命中的规则 id。通用兜底为 "fallback" */
    val ruleId: String,
    /** 命中的关键词，例如 "跳过" */
    val matchedKeyword: String,
    /** 被点击控件上的原始文字，例如 "跳过 5" */
    val matchedText: String,
    /** 点击方式，见 ClickMethod */
    val clickMethod: String,
    /** 页面上是否出现了"摇一摇"字样 */
    val shakeDetected: Boolean,
    /** 从 App 打开到点击的用时，毫秒 */
    val elapsedSinceLaunchMs: Long,
)

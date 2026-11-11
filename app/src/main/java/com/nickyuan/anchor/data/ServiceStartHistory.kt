package com.nickyuan.anchor.data

/**
 * 跳过服务最近几次启动的时间（毫秒时间戳），在 DataStore 里存成用逗号分隔的一个字符串。
 *
 * 用途：验证"锁屏放一晚后服务仍在运行"。如果早上看到上一次启动是昨天，说明整晚没被杀过；
 * 如果半夜有启动记录，说明被系统杀掉后又自己起来了（保活设置起了作用，但中间有一段没在运行）。
 */
object ServiceStartHistory {

    const val MAX_ENTRIES = 10

    /** 从旧到新。格式不对的内容直接忽略 */
    fun parse(raw: String?): List<Long> =
        raw.orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }

    /** 追加一次启动，只保留最近 [max] 条 */
    fun append(raw: String?, startedAt: Long, max: Int = MAX_ENTRIES): String =
        (parse(raw) + startedAt).takeLast(max).joinToString(",")
}

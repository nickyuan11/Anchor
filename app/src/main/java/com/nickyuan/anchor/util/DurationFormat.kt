package com.nickyuan.anchor.util

object DurationFormat {

    /** 把毫秒数写成"3 天 2 小时""5 小时 12 分""8 分钟"这样给人看的时长 */
    fun humanize(durationMs: Long): String {
        val totalMinutes = durationMs.coerceAtLeast(0) / 60_000
        val days = totalMinutes / (24 * 60)
        val hours = totalMinutes / 60 % 24
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "$days 天 $hours 小时"
            hours > 0 -> "$hours 小时 $minutes 分"
            minutes > 0 -> "$minutes 分钟"
            else -> "不到 1 分钟"
        }
    }
}

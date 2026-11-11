package com.nickyuan.anchor.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

    private val minute = 60_000L
    private val hour = 60 * minute

    @Test
    fun `各种时长`() {
        assertEquals("不到 1 分钟", DurationFormat.humanize(59_000))
        assertEquals("8 分钟", DurationFormat.humanize(8 * minute + 30_000))
        assertEquals("5 小时 12 分", DurationFormat.humanize(5 * hour + 12 * minute))
        assertEquals("9 小时 0 分", DurationFormat.humanize(9 * hour))
        assertEquals("3 天 2 小时", DurationFormat.humanize(74 * hour + 40 * minute))
    }

    @Test
    fun `负数按零处理`() {
        assertEquals("不到 1 分钟", DurationFormat.humanize(-5))
    }
}

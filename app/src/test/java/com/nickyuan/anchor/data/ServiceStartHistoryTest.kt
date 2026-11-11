package com.nickyuan.anchor.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceStartHistoryTest {

    @Test
    fun `空内容和格式不对的内容都能处理`() {
        assertEquals(emptyList<Long>(), ServiceStartHistory.parse(null))
        assertEquals(emptyList<Long>(), ServiceStartHistory.parse(""))
        assertEquals(listOf(1L, 3L), ServiceStartHistory.parse("1,abc, 3"))
    }

    @Test
    fun `追加后能读回来，顺序从旧到新`() {
        val raw = ServiceStartHistory.append(ServiceStartHistory.append(null, 100), 200)
        assertEquals(listOf(100L, 200L), ServiceStartHistory.parse(raw))
    }

    @Test
    fun `只保留最近若干条`() {
        var raw: String? = null
        for (i in 1..15L) raw = ServiceStartHistory.append(raw, i, max = 10)
        assertEquals((6..15L).toList(), ServiceStartHistory.parse(raw))
    }
}

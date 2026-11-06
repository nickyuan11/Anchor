package com.nickyuan.anchor.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class DiagnosticsLogTest {

    private val log = DiagnosticsLog(capacity = 3, clock = { LocalTime.of(17, 26, 6, 200_000_000) })

    @Test
    fun `每条带毫秒时间戳`() {
        log.add("hello")
        assertEquals(listOf("17:26:06.200 hello"), log.snapshot())
    }

    @Test
    fun `超过容量时丢掉最旧的`() {
        (1..5).forEach { log.add("#$it") }
        assertEquals(listOf("#3", "#4", "#5"), log.snapshot().map { it.substringAfter(' ') })
    }
}

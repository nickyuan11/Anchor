package com.nickyuan.anchor.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebouncerTest {

    /** 假时钟：测试里手动拨动时间 */
    private var now = 0L
    private val debouncer = Debouncer(windowMs = 2_000, clock = { now })

    @Test
    fun `第一次放行，窗口内同一个键不再放行`() {
        assertTrue(debouncer.tryAcquire("a"))
        now = 1_999
        assertFalse(debouncer.tryAcquire("a"))
    }

    @Test
    fun `窗口过后再次放行`() {
        assertTrue(debouncer.tryAcquire("a"))
        now = 2_000
        assertTrue(debouncer.tryAcquire("a"))
    }

    @Test
    fun `不同的键互不影响`() {
        assertTrue(debouncer.tryAcquire("a"))
        assertTrue(debouncer.tryAcquire("b"))
    }

    @Test
    fun `被拒绝的尝试不会延长窗口`() {
        assertTrue(debouncer.tryAcquire("a"))
        now = 1_500
        assertFalse(debouncer.tryAcquire("a"))
        now = 2_000
        assertTrue(debouncer.tryAcquire("a"))
    }
}

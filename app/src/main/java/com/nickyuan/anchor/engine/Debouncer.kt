package com.nickyuan.anchor.engine

/**
 * 防抖：同一个键在时间窗口内只放行一次，避免对同一个按钮连点。
 *
 * 键通常是"包名|页面|规则 id"（design.md 第 5 节）。
 * 注意：不是线程安全的。无障碍服务里所有规则处理都在同一个单线程调度器上执行，所以不需要加锁。
 *
 * @param windowMs 时间窗口，单位毫秒
 * @param clock 获取当前时间的函数。正式代码用系统时钟，单元测试里可以传入假时钟，方便控制时间
 */
class Debouncer(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val clock: () -> Long,
) {
    private val lastAcquiredAt = HashMap<String, Long>()

    /** 如果这个键可以执行就返回 true，并记下执行时间；还在窗口期内则返回 false */
    fun tryAcquire(key: String): Boolean {
        val now = clock()
        val last = lastAcquiredAt[key]
        if (last != null && now - last < windowMs) return false
        lastAcquiredAt[key] = now
        // 顺手清理过期的记录，防止 HashMap 无限变大
        if (lastAcquiredAt.size > MAX_ENTRIES) {
            lastAcquiredAt.entries.removeAll { now - it.value >= windowMs }
        }
        return true
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 2_000L
        private const val MAX_ENTRIES = 64
    }
}

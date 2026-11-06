package com.nickyuan.anchor.util

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 诊断日志：在内存里保留最近的若干条服务内部事件（窗口变化、点击尝试、手势结果……），
 * 抓页面快照时一起导出。
 *
 * 朋友的手机不在手边，看不到 Logcat；有了它，一份快照就能看清服务内部发生了什么。
 * 只存在内存里，不写文件，App 进程结束就清空。
 *
 * 环形缓冲：满了以后丢掉最旧的一条，内存占用固定。
 * synchronized：服务的引擎线程和主线程都会写，加锁保证线程安全（类似 Java 的 synchronized 方法）。
 */
@Singleton
class DiagnosticsLog(
    private val capacity: Int,
    private val clock: () -> LocalTime,
) {
    /** Hilt 使用的构造函数：保留 200 条，用系统时间 */
    @Inject constructor() : this(DEFAULT_CAPACITY, LocalTime::now)

    private val lines = ArrayDeque<String>()
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    @Synchronized
    fun add(message: String) {
        if (lines.size >= capacity) lines.removeFirst()
        lines.addLast("${clock().format(timeFormat)} $message")
    }

    /** 当前所有记录，从旧到新 */
    @Synchronized
    fun snapshot(): List<String> = lines.toList()

    private companion object {
        const val DEFAULT_CAPACITY = 200
    }
}

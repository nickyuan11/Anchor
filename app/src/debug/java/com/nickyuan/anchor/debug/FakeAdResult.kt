package com.nickyuan.anchor.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 一次模拟广告测试的结果。
 *
 * sealed interface（密封接口）：所有实现类都必须写在这个文件里，
 * 编译器因此知道一共只有这几种情况，用 when 判断时漏写一种会直接报错。
 * 可以理解为"能携带数据的枚举"。
 */
sealed interface FakeAdResult {
    /** 结果发生的时间，方便区分多次测试 */
    val at: LocalTime

    /** 点了"跳过"按钮。elapsedMs = 广告出现到点击的用时 */
    data class Skipped(val elapsedMs: Long, override val at: LocalTime = LocalTime.now()) : FakeAdResult

    /** 倒计时走完，自动进入主页 */
    data class CountdownFinished(override val at: LocalTime = LocalTime.now()) : FakeAdResult

    /** 摇一摇被触发，跳到了广告详情页（也就是"被坑了"） */
    data class Shaken(val elapsedMs: Long, override val at: LocalTime = LocalTime.now()) : FakeAdResult

    /** 按返回键退出了广告页 */
    data class BackPressed(val elapsedMs: Long, override val at: LocalTime = LocalTime.now()) : FakeAdResult
}

/** 转成主页上显示的一行文字 */
fun FakeAdResult.describe(): String {
    val time = at.format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    val text = when (this) {
        is FakeAdResult.Skipped -> "✅ 已跳过，用时 $elapsedMs ms"
        is FakeAdResult.CountdownFinished -> "⏱️ 倒计时结束，自动进入主页"
        is FakeAdResult.Shaken -> "❌ 被摇一摇跳转到广告详情页（$elapsedMs ms）"
        is FakeAdResult.BackPressed -> "↩️ 按返回键退出（$elapsedMs ms）"
    }
    return "[$time] $text"
}

/**
 * 保存"最近一次测试结果"，供主页显示。
 *
 * object：Kotlin 的单例写法，相当于 Java 里手写的单例类。
 * StateFlow：一个"会通知变化的值"。主页订阅它，这里一更新，主页界面自动刷新
 * （有点像观察者模式）。只存在内存里，App 进程被杀后清空，测试用足够了。
 */
object FakeAdResultStore {
    private val _last = MutableStateFlow<FakeAdResult?>(null)
    val last: StateFlow<FakeAdResult?> = _last.asStateFlow()

    fun record(result: FakeAdResult) {
        _last.value = result
    }
}

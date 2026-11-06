package com.nickyuan.anchor.engine

/**
 * 记录每条规则在"本次打开"中执行了几次，用于 maxClicks 限制。
 * App 每次重新打开（[LaunchTracker.launchCount] 变化）时清零。
 * 和 Debouncer 一样只在服务的单线程调度器上使用，不需要加锁。
 */
class PerLaunchCounter {
    private var launchId = -1L
    private val counts = HashMap<String, Int>()

    /** 读取次数前先调用，传入当前的打开编号；编号变了就清零 */
    fun syncLaunch(currentLaunchId: Long) {
        if (currentLaunchId != launchId) {
            launchId = currentLaunchId
            counts.clear()
        }
    }

    fun count(key: String): Int = counts[key] ?: 0

    fun increment(key: String) {
        counts[key] = count(key) + 1
    }
}

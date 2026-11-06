package com.nickyuan.anchor.engine

/**
 * 跟踪"当前前台是哪个 App、哪个页面、什么时候打开的"。
 *
 * 只有"切换到某个 App 的页面窗口"才算换了前台 App，判断依据（由服务提供）是满足其一：
 * - 窗口类名能查到是这个 App 的 Activity；
 * - 或者当前用户正在操作的窗口（活动窗口）就属于这个 App。
 *   爱奇艺这类插件化 App 的页面查不到 Activity，靠这一条识别（M3 真机快照）。
 *
 * 悬浮按钮、输入法、鸿蒙的手势导航条等窗口不会成为活动窗口，它们的变化一律不影响判断——
 * 否则点一下 📷、弹一下键盘，就会被误认为"切到了另一个 App"（M3 真机快照里发现的问题）。
 *
 * 前台 App 的包名一变化，就认为这个 App "刚刚打开"，开始计时；
 * 打开后 [launchWindowMs] 毫秒内是"启动窗口期"，通用兜底只在窗口期内生效（design.md 第 2 节）。
 *
 * 和 Debouncer 一样不是线程安全的，只在无障碍服务的单线程调度器上使用。
 *
 * @param ignoredPackages 不算"切换 App"的包，例如下拉通知栏（systemui）。
 *        否则拉一下通知栏再收起，就会被当成重新打开了 App。
 */
class LaunchTracker(
    private val launchWindowMs: Long = DEFAULT_LAUNCH_WINDOW_MS,
    private val ignoredPackages: Set<String> = DEFAULT_IGNORED_PACKAGES,
    private val clock: () -> Long,
) {
    /** 当前前台 App 的包名 */
    var foregroundPackage: String? = null
        private set

    /** 当前前台页面（Activity）的完整类名；未知时为 null */
    var currentActivity: String? = null
        private set

    private var launchedAt: Long = 0L

    /**
     * 收到"窗口状态变化"（换页面）事件时调用。
     *
     * @param activityName 如果这个窗口是一个 Activity，传它的完整类名；否则传 null
     * @param isAppWindow 这个窗口是否是 App 的页面窗口（见类注释）。
     *        为 false 时（弹窗、悬浮窗、输入法等）什么都不改变
     * @return 是否是一次新的 App 打开
     */
    fun onWindowStateChanged(
        packageName: String,
        activityName: String?,
        isAppWindow: Boolean = activityName != null,
    ): Boolean {
        if (packageName in ignoredPackages) return false
        if (!isAppWindow) return false

        val isNewLaunch = packageName != foregroundPackage
        if (isNewLaunch) {
            foregroundPackage = packageName
            launchedAt = clock()
        }
        // 查不到 Activity 名时：换了 App 就清空；同一个 App 内保持原来的页面名
        if (activityName != null || isNewLaunch) currentActivity = activityName
        return isNewLaunch
    }

    /** 这个包是否是前台 App，并且处于启动窗口期内 */
    fun isInLaunchWindow(packageName: String): Boolean =
        packageName == foregroundPackage && elapsedSinceLaunchMs() <= launchWindowMs

    /** 距离前台 App 打开过了多少毫秒 */
    fun elapsedSinceLaunchMs(): Long = clock() - launchedAt

    companion object {
        const val DEFAULT_LAUNCH_WINDOW_MS = 5_000L
        val DEFAULT_IGNORED_PACKAGES = setOf("com.android.systemui")
    }
}

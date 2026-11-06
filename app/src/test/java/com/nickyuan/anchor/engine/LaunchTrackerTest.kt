package com.nickyuan.anchor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchTrackerTest {

    private var now = 10_000L
    private val tracker = LaunchTracker(launchWindowMs = 5_000, clock = { now })

    @Test
    fun `切换到新 App 的页面算一次打开，并进入启动窗口期`() {
        assertTrue(tracker.onWindowStateChanged("com.a", "com.a.SplashActivity"))
        assertTrue(tracker.isInLaunchWindow("com.a"))
        assertEquals("com.a.SplashActivity", tracker.currentActivity)
    }

    @Test
    fun `超过窗口期后不再生效`() {
        tracker.onWindowStateChanged("com.a", "com.a.Main")
        now += 5_000
        assertTrue(tracker.isInLaunchWindow("com.a"))
        now += 1
        assertFalse(tracker.isInLaunchWindow("com.a"))
    }

    @Test
    fun `同一个 App 内换页面不会重新计时`() {
        tracker.onWindowStateChanged("com.a", "com.a.SplashActivity")
        now += 3_000
        assertFalse(tracker.onWindowStateChanged("com.a", "com.a.MainActivity"))
        assertEquals(3_000, tracker.elapsedSinceLaunchMs())
        assertEquals("com.a.MainActivity", tracker.currentActivity)
    }

    @Test
    fun `同一个 App 的弹窗不改变当前页面`() {
        tracker.onWindowStateChanged("com.a", "com.a.SplashActivity")
        assertFalse(tracker.onWindowStateChanged("com.a", null))
        assertEquals("com.a.SplashActivity", tracker.currentActivity)
    }

    @Test
    fun `其他包的非 Activity 窗口（悬浮按钮、输入法、手势导航条）不算切换 App`() {
        tracker.onWindowStateChanged("com.a", "com.a.SplashActivity")
        now += 1_000
        assertFalse(tracker.onWindowStateChanged("com.nickyuan.anchor", null)) // 📷 悬浮按钮
        assertFalse(tracker.onWindowStateChanged("com.sohu.inputmethod", null)) // 输入法
        assertFalse(tracker.onWindowStateChanged("android", null)) // 鸿蒙手势导航条
        assertEquals("com.a", tracker.foregroundPackage)
        assertEquals("com.a.SplashActivity", tracker.currentActivity)
        assertEquals(1_000, tracker.elapsedSinceLaunchMs())
        assertTrue(tracker.isInLaunchWindow("com.a"))
    }

    @Test
    fun `其他 App 不在窗口期内`() {
        tracker.onWindowStateChanged("com.a", "com.a.Main")
        assertFalse(tracker.isInLaunchWindow("com.b"))
    }

    @Test
    fun `下拉通知栏不算切换 App`() {
        tracker.onWindowStateChanged("com.a", "com.a.MainActivity")
        now += 10_000
        assertFalse(tracker.onWindowStateChanged("com.android.systemui", "com.android.systemui.Shade"))
        assertEquals("com.a", tracker.foregroundPackage)
        assertFalse(tracker.isInLaunchWindow("com.a"))
    }

    @Test
    fun `一开始什么都不知道`() {
        assertNull(tracker.foregroundPackage)
        assertNull(tracker.currentActivity)
        assertFalse(tracker.isInLaunchWindow("com.a"))
    }

    @Test
    fun `离开再回来重新计时`() {
        tracker.onWindowStateChanged("com.a", "com.a.Main")
        now += 60_000
        tracker.onWindowStateChanged("com.launcher", "com.launcher.Home")
        assertTrue(tracker.onWindowStateChanged("com.a", "com.a.Main"))
        assertTrue(tracker.isInLaunchWindow("com.a"))
    }
}

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
    fun `切换到新 App 算一次打开，并进入启动窗口期`() {
        assertTrue(tracker.onWindowStateChanged("com.a", "com.a.SplashActivity"))
        assertTrue(tracker.isInLaunchWindow("com.a"))
        assertEquals("com.a.SplashActivity", tracker.currentActivity)
    }

    @Test
    fun `超过窗口期后不再生效`() {
        tracker.onWindowStateChanged("com.a", null)
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
    fun `非 Activity 窗口不改变当前页面`() {
        tracker.onWindowStateChanged("com.a", "com.a.SplashActivity")
        tracker.onWindowStateChanged("com.a", null)
        assertEquals("com.a.SplashActivity", tracker.currentActivity)
    }

    @Test
    fun `其他 App 不在窗口期内`() {
        tracker.onWindowStateChanged("com.a", null)
        assertFalse(tracker.isInLaunchWindow("com.b"))
    }

    @Test
    fun `下拉通知栏不算切换 App`() {
        tracker.onWindowStateChanged("com.a", "com.a.MainActivity")
        now += 10_000
        assertFalse(tracker.onWindowStateChanged("com.android.systemui", null))
        assertEquals("com.a", tracker.foregroundPackage)
        assertFalse(tracker.isInLaunchWindow("com.a"))
    }

    @Test
    fun `切到新 App 时清掉旧页面`() {
        tracker.onWindowStateChanged("com.a", "com.a.MainActivity")
        tracker.onWindowStateChanged("com.b", null)
        assertNull(tracker.currentActivity)
    }

    @Test
    fun `离开再回来重新计时`() {
        tracker.onWindowStateChanged("com.a", null)
        now += 60_000
        tracker.onWindowStateChanged("com.launcher", null)
        assertTrue(tracker.onWindowStateChanged("com.a", null))
        assertTrue(tracker.isInLaunchWindow("com.a"))
    }
}

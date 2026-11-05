package com.nickyuan.anchor.debug

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ShakeDetector 的单元测试。直接在电脑上运行，不需要模拟器。
 * 运行方式：在 Android Studio 里点类名左边的绿色三角，或命令行执行 gradlew testDebugUnitTest
 */
class ShakeDetectorTest {

    private val g = ShakeDetector.STANDARD_GRAVITY

    @Test
    fun `静止平放时不算摇`() {
        assertFalse(ShakeDetector.isShake(0f, 0f, g))
    }

    @Test
    fun `竖着拿或侧放时也不算摇`() {
        assertFalse(ShakeDetector.isShake(0f, g, 0f))
        assertFalse(ShakeDetector.isShake(g * 0.7f, g * 0.7f, 0f))
    }

    @Test
    fun `轻微晃动低于阈值不算摇`() {
        assertFalse(ShakeDetector.isShake(g * 0.5f, 0f, g))
    }

    @Test
    fun `用力晃动超过阈值算摇`() {
        assertTrue(ShakeDetector.isShake(g * 2f, 0f, g))
    }

    @Test
    fun `略高于阈值算摇`() {
        assertTrue(ShakeDetector.isShake(0f, 0f, g * (ShakeDetector.DEFAULT_THRESHOLD_G + 0.01f)))
    }

    @Test
    fun `可以使用自定义阈值`() {
        assertTrue(ShakeDetector.isShake(g * 0.5f, 0f, g, thresholdG = 1.1f))
        assertFalse(ShakeDetector.isShake(g * 2f, 0f, g, thresholdG = 3f))
    }
}

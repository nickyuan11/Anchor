package com.nickyuan.anchor.debug

import kotlin.math.sqrt

/**
 * 摇一摇判断逻辑。
 *
 * 故意写成不依赖任何 Android 类的纯函数，这样可以直接在电脑上跑 JUnit 测试，
 * 不需要启动模拟器（测试见 src/testDebug/.../ShakeDetectorTest.kt）。
 *
 * 原理：加速度传感器给出 x、y、z 三个方向的加速度（单位 m/s²）。
 * 手机静止时三者合起来约等于重力加速度 1g（9.8 m/s²）；用力晃动时会瞬间变大。
 * 合加速度超过阈值，就认为摇了一下。很多广告 SDK 的摇一摇也是这么判断的。
 */
object ShakeDetector {

    /** 标准重力加速度，单位 m/s² */
    const val STANDARD_GRAVITY = 9.80665f

    /**
     * 触发阈值：合加速度达到几倍重力算"摇了"。
     * 如果模拟器上很难触发，可以调低（例如 1.3f）；真机上太灵敏就调高。
     */
    const val DEFAULT_THRESHOLD_G = 1.8f

    /**
     * @param x、y、z 三个方向的加速度，单位 m/s²
     * @return 是否算作一次摇一摇
     */
    fun isShake(x: Float, y: Float, z: Float, thresholdG: Float = DEFAULT_THRESHOLD_G): Boolean {
        val gForce = sqrt(x * x + y * y + z * z) / STANDARD_GRAVITY
        return gForce >= thresholdG
    }
}

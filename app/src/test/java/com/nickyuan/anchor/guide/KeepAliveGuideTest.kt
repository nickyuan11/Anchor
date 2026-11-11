package com.nickyuan.anchor.guide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepAliveGuideTest {

    @Test
    fun `按厂商或品牌识别，不区分大小写`() {
        assertEquals(DeviceBrand.XIAOMI, DeviceBrand.detect("Xiaomi", "Redmi")) // 红米 K70
        assertEquals(DeviceBrand.HUAWEI, DeviceBrand.detect("HUAWEI", "HUAWEI")) // Mate40
        assertEquals(DeviceBrand.HUAWEI, DeviceBrand.detect("HONOR", "HONOR"))
        assertEquals(DeviceBrand.XIAOMI, DeviceBrand.detect("unknown", "POCO"))
        assertEquals(DeviceBrand.OTHER, DeviceBrand.detect("Google", "google"))
        assertEquals(DeviceBrand.OTHER, DeviceBrand.detect(null, null))
    }

    @Test
    fun `每个品牌都有步骤，步骤 id 全局不重复`() {
        val all = DeviceBrand.entries.flatMap { KeepAliveGuide.stepsFor(it) }
        assertTrue(DeviceBrand.entries.all { KeepAliveGuide.stepsFor(it).isNotEmpty() })
        assertEquals(all.size, all.map { it.id }.toSet().size)
    }

    @Test
    fun `有跳转目标的步骤一定有按钮文字，反之亦然`() {
        for (step in DeviceBrand.entries.flatMap { KeepAliveGuide.stepsFor(it) }) {
            assertEquals(step.id, step.target != null, step.buttonLabel != null)
        }
    }

    @Test
    fun `小米有自启动和省电策略，华为有应用启动管理`() {
        val xiaomi = KeepAliveGuide.stepsFor(DeviceBrand.XIAOMI).mapNotNull { it.target }
        assertEquals(listOf(KeepAliveTarget.AUTO_START, KeepAliveTarget.BATTERY_SAVER), xiaomi)
        val huawei = KeepAliveGuide.stepsFor(DeviceBrand.HUAWEI).mapNotNull { it.target }
        assertEquals(listOf(KeepAliveTarget.AUTO_START), huawei)
    }
}

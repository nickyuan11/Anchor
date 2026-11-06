package com.nickyuan.anchor.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackMatcherTest {

    // 模拟一块 1080 x 2400 的屏幕
    private val w = 1080
    private val h = 2400
    private val matcher = FallbackMatcher()

    /** 造一个控件：给出中心点和宽高 */
    private fun node(
        text: String?,
        cx: Int,
        cy: Int,
        width: Int = 160,
        height: Int = 70,
        clickable: Boolean = true,
        visible: Boolean = true,
        desc: String? = null,
    ) = NodeInfo(
        text = text,
        contentDescription = desc,
        viewId = null,
        bounds = Bounds(cx - width / 2, cy - height / 2, cx + width / 2, cy + height / 2),
        isClickable = clickable,
        isVisibleToUser = visible,
    )

    @Test
    fun `右上角的跳过倒计时按钮会被选中`() {
        val match = matcher.findTarget(listOf(node("跳过 5", cx = 980, cy = 120)), w, h)
        assertNotNull(match)
        assertEquals("跳过", match!!.keyword)
        assertEquals("跳过 5", match.matchedText)
    }

    @Test
    fun `右下角和左上角也算角落`() {
        assertNotNull(matcher.findTarget(listOf(node("跳过", cx = 980, cy = 2300)), w, h))
        assertNotNull(matcher.findTarget(listOf(node("跳过", cx = 100, cy = 120)), w, h))
    }

    @Test
    fun `英文 Skip 不区分大小写`() {
        assertNotNull(matcher.findTarget(listOf(node("SKIP AD 3s", cx = 980, cy = 120)), w, h))
    }

    @Test
    fun `只有内容描述没有文字的图片按钮也能匹配`() {
        assertNotNull(matcher.findTarget(listOf(node(text = null, desc = "跳过广告", cx = 980, cy = 120)), w, h))
    }

    @Test
    fun `屏幕中间的跳过不点`() {
        assertNull(matcher.findTarget(listOf(node("跳过", cx = 540, cy = 1200)), w, h))
    }

    @Test
    fun `顶部正中间的跳过不点`() {
        assertNull(matcher.findTarget(listOf(node("跳过", cx = 540, cy = 120)), w, h))
    }

    @Test
    fun `长句子不点`() {
        assertNull(matcher.findTarget(listOf(node("本次已为你跳过 3 个开屏广告", cx = 980, cy = 120)), w, h))
    }

    @Test
    fun `太大的控件不点`() {
        assertNull(matcher.findTarget(listOf(node("跳过", cx = 900, cy = 200, width = 600)), w, h))
    }

    @Test
    fun `看不见的控件不点`() {
        assertNull(matcher.findTarget(listOf(node("跳过", cx = 980, cy = 120, visible = false)), w, h))
    }

    @Test
    fun `不含关键词的不点`() {
        assertNull(matcher.findTarget(listOf(node("关闭", cx = 980, cy = 120)), w, h))
    }

    @Test
    fun `有多个候选时优先可点击的`() {
        val textOnly = node("跳过", cx = 980, cy = 120, width = 80, height = 40, clickable = false)
        val button = node("跳过 5", cx = 980, cy = 120, width = 160, height = 70, clickable = true)
        assertEquals(button, matcher.findTarget(listOf(textOnly, button), w, h)!!.node)
    }

    @Test
    fun `都可点击时选面积小的`() {
        val big = node("跳过", cx = 980, cy = 120, width = 200, height = 90)
        val small = node("跳过", cx = 980, cy = 120, width = 120, height = 50)
        assertEquals(small, matcher.findTarget(listOf(big, small), w, h)!!.node)
    }

    // ---- 有条件的关键词"关闭"（爱奇艺开屏广告，屏幕 1200 x 2640） ----

    @Test
    fun `页面有广告特征时，右上角的关闭会被选中`() {
        val close = node("关闭", cx = 1001, cy = 125, width = 265, height = 119)
        val match = matcher.findTarget(listOf(close), 1200, 2640, adMarkersPresent = true)
        assertEquals("关闭", match!!.keyword)
    }

    @Test
    fun `页面没有广告特征时，关闭不点`() {
        val close = node("关闭", cx = 1001, cy = 125, width = 265, height = 119)
        assertNull(matcher.findTarget(listOf(close), 1200, 2640, adMarkersPresent = false))
    }

    @Test
    fun `跳过和关闭同时存在时优先跳过`() {
        val close = node("关闭", cx = 980, cy = 120, width = 100, height = 50)
        val skip = node("跳过", cx = 980, cy = 2300, width = 160, height = 70)
        assertEquals(skip, matcher.findTarget(listOf(close, skip), w, h, adMarkersPresent = true)!!.node)
    }

    @Test
    fun `只有关闭类候选时才需要检查广告特征`() {
        assertTrue(matcher.hasConditionalCandidate(listOf(node("关闭", cx = 980, cy = 120))))
        assertFalse(matcher.hasConditionalCandidate(listOf(node("跳过", cx = 980, cy = 120))))
        // "跳过并关闭"已经被明确关键词命中，不需要再查
        assertFalse(matcher.hasConditionalCandidate(listOf(node("跳过并关闭", cx = 980, cy = 120))))
    }

    @Test
    fun `关键词列表包含有条件的关闭，广告特征包含扭转手机`() {
        assertTrue("关闭" in matcher.allKeywords)
        assertTrue("扭转手机" in matcher.adMarkers)
        assertTrue("翻转手机" in FallbackConfig.SHAKE_KEYWORDS)
    }

    @Test
    fun `屏幕尺寸异常时返回空`() {
        assertNull(matcher.findTarget(listOf(node("跳过", cx = 980, cy = 120)), 0, 0))
    }
}

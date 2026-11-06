package com.nickyuan.anchor.snapshot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class SnapshotNamingTest {

    private val time = LocalDateTime.of(2026, 11, 6, 15, 30, 12)

    @Test
    fun `生成文件名`() {
        assertEquals(
            "snapshot_com.example.app_20261106-153012.zip",
            SnapshotNaming.fileName("com.example.app", time),
        )
    }

    @Test
    fun `能把生成的文件名解析回来`() {
        val parsed = SnapshotNaming.parse(SnapshotNaming.fileName("com.example.app", time))
        assertEquals(SnapshotNaming.Parsed("com.example.app", time), parsed)
    }

    @Test
    fun `包名里带下划线也能正确解析`() {
        val parsed = SnapshotNaming.parse(SnapshotNaming.fileName("com.foo_bar.app", time))
        assertEquals("com.foo_bar.app", parsed!!.packageName)
    }

    @Test
    fun `不是快照文件时返回空`() {
        assertNull(SnapshotNaming.parse("photo.jpg"))
        assertNull(SnapshotNaming.parse("snapshot_com.example.app.zip"))
        assertNull(SnapshotNaming.parse("snapshot_com.example.app_not-a-time.zip"))
    }
}

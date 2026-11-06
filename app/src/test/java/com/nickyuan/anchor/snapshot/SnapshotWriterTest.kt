package com.nickyuan.anchor.snapshot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class SnapshotWriterTest {

    private val snapshot = Snapshot(
        capturedAt = "2026-11-06T15:30:12",
        app = AppSnapshot("com.example.app", "示例 App", "1.0", 1, "com.example.app.SplashActivity", 800),
        device = DeviceSnapshot("Xiaomi", "Redmi", "K70", "14", 34, "OS2.0", 1440, 3200),
        windows = listOf(
            WindowSnapshot(
                type = "application",
                title = "示例 App",
                packageName = "com.example.app",
                isActive = true,
                layer = 1,
                bounds = "0,0,1440,3200",
                root = NodeSnapshot(
                    className = "android.widget.FrameLayout",
                    bounds = "0,0,1440,3200",
                    children = listOf(
                        NodeSnapshot(
                            className = "android.widget.TextView",
                            text = "跳过 5",
                            viewId = "com.example.app:id/skip",
                            bounds = "1200,100,1400,180",
                            clickable = true,
                        ),
                    ),
                ),
            ),
        ),
        recentSkipLogs = emptyList(),
        hasScreenshot = true,
    )

    /** 读出 zip 里所有文件：文件名 → 内容 */
    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result[entry.name] = zip.readBytes()
            }
        }
        return result
    }

    @Test
    fun `zip 里有 JSON 和截图，JSON 能还原成同样的快照`() {
        val jpeg = byteArrayOf(1, 2, 3)
        val out = ByteArrayOutputStream()
        SnapshotWriter.write(snapshot, jpeg, out)

        val files = unzip(out.toByteArray())
        assertEquals(setOf(SnapshotWriter.JSON_ENTRY, SnapshotWriter.SCREENSHOT_ENTRY), files.keys)
        assertArrayEquals(jpeg, files[SnapshotWriter.SCREENSHOT_ENTRY])

        val json = files.getValue(SnapshotWriter.JSON_ENTRY).toString(Charsets.UTF_8)
        assertEquals(snapshot, SnapshotWriter.json.decodeFromString(Snapshot.serializer(), json))
    }

    @Test
    fun `没有截图时 zip 里只有 JSON`() {
        val out = ByteArrayOutputStream()
        SnapshotWriter.write(snapshot.copy(hasScreenshot = false), null, out)
        assertEquals(setOf(SnapshotWriter.JSON_ENTRY), unzip(out.toByteArray()).keys)
    }

    @Test
    fun `JSON 里是中文原文，带格式版本号，其他默认值字段不写出来`() {
        val out = ByteArrayOutputStream()
        SnapshotWriter.write(snapshot, null, out)
        val json = unzip(out.toByteArray()).getValue(SnapshotWriter.JSON_ENTRY).toString(Charsets.UTF_8)

        assertTrue(json.contains("\"formatVersion\": ${Snapshot.FORMAT_VERSION}"))
        assertTrue(json.contains("\"text\": \"跳过 5\""))
        assertTrue(json.contains("\"viewId\": \"com.example.app:id/skip\""))
        // FrameLayout 不可点击，clickable=false 是默认值，不应出现
        assertFalse(json.contains("\"clickable\": false"))
    }
}

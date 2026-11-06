package com.nickyuan.anchor.snapshot

import kotlinx.serialization.json.Json
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 把快照写成 zip：
 * - snapshot.json：页面结构等信息（见 SnapshotModels.kt）
 * - screenshot.jpg：截图（有的话）
 *
 * 纯 Kotlin + JDK，不依赖 Android，可以直接做单元测试。
 */
object SnapshotWriter {

    const val JSON_ENTRY = "snapshot.json"
    const val SCREENSHOT_ENTRY = "screenshot.jpg"

    /**
     * JSON 格式配置：
     * - prettyPrint：带缩进换行，方便人直接阅读
     * - encodeDefaults = false：值等于默认值的字段不写出来（例如 clickable=false），文件小很多
     */
    val json = Json {
        prettyPrint = true
        encodeDefaults = false
    }

    fun write(snapshot: Snapshot, screenshotJpeg: ByteArray?, out: OutputStream) {
        // use { }：用完自动关闭，相当于 Java 的 try-with-resources
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(json.encodeToString(Snapshot.serializer(), snapshot).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            if (screenshotJpeg != null) {
                zip.putNextEntry(ZipEntry(SCREENSHOT_ENTRY))
                zip.write(screenshotJpeg)
                zip.closeEntry()
            }
        }
    }
}

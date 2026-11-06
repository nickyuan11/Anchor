package com.nickyuan.anchor.snapshot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

/** 一份已保存的快照文件 */
data class SnapshotEntry(
    val file: File,
    val packageName: String,
    val capturedAt: LocalDateTime,
    val sizeBytes: Long,
)

/**
 * 快照文件的保存、列出、删除。
 * 文件放在 App 私有目录 files/snapshots/ 下，其他 App 读不到；分享时通过 FileProvider 临时授权。
 *
 * 无障碍服务和界面在同一个进程里，拿到的是同一个 @Singleton 实例，
 * 所以服务一保存，列表页就能通过 [entries] 立刻看到。
 */
@Singleton
class SnapshotRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir = File(context.filesDir, DIR_NAME)

    private val _entries = MutableStateFlow<List<SnapshotEntry>>(emptyList())

    /** 所有快照，按时间倒序 */
    val entries: StateFlow<List<SnapshotEntry>> = _entries.asStateFlow()

    /** 保存一份快照，返回生成的 zip 文件 */
    suspend fun save(snapshot: Snapshot, screenshotJpeg: ByteArray?, capturedAt: LocalDateTime): File =
        withContext(Dispatchers.IO) { // 文件读写放到 IO 线程
            dir.mkdirs()
            val file = File(dir, SnapshotNaming.fileName(snapshot.app.packageName, capturedAt))
            file.outputStream().use { SnapshotWriter.write(snapshot, screenshotJpeg, it) }
            refreshInternal()
            file
        }

    /** 重新扫描目录，刷新 [entries] */
    suspend fun refresh() = withContext(Dispatchers.IO) { refreshInternal() }

    suspend fun delete(entry: SnapshotEntry) = withContext(Dispatchers.IO) {
        entry.file.delete()
        refreshInternal()
    }

    /** 读取 zip 里的截图，缩小后用作列表缩略图；没有截图时返回 null */
    suspend fun loadThumbnail(entry: SnapshotEntry, targetWidth: Int = 160): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                ZipFile(entry.file).use { zip ->
                    val zipEntry = zip.getEntry(SnapshotWriter.SCREENSHOT_ENTRY) ?: return@use null
                    // 先只读尺寸，算出缩小倍数，再真正解码，避免把整张大图读进内存
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    zip.getInputStream(zipEntry).use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sampleSize = 1
                    while (bounds.outWidth / (sampleSize * 2) >= targetWidth) sampleSize *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                    zip.getInputStream(zipEntry).use { BitmapFactory.decodeStream(it, null, options) }
                }
            }.getOrNull()
        }

    private fun refreshInternal() {
        _entries.value = (dir.listFiles() ?: emptyArray())
            .mapNotNull { file ->
                val parsed = SnapshotNaming.parse(file.name) ?: return@mapNotNull null
                SnapshotEntry(file, parsed.packageName, parsed.capturedAt, file.length())
            }
            .sortedByDescending { it.capturedAt }
    }

    companion object {
        /** 与 res/xml/file_paths.xml 里的 path 对应 */
        const val DIR_NAME = "snapshots"
    }
}

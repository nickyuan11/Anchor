package com.nickyuan.anchor.snapshot

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 快照 zip 的文件名规则：snapshot_<包名>_<yyyyMMdd-HHmmss>.zip
 * 例如 snapshot_com.example.app_20261106-153012.zip
 *
 * 列表页靠文件名就能知道是哪个 App、什么时间，不用每次都打开 zip 读 JSON。
 */
object SnapshotNaming {

    private const val PREFIX = "snapshot_"
    private const val SUFFIX = ".zip"
    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    /** 从文件名解析出来的信息 */
    data class Parsed(val packageName: String, val capturedAt: LocalDateTime)

    fun fileName(packageName: String, capturedAt: LocalDateTime): String =
        "$PREFIX${packageName}_${capturedAt.format(TIME_FORMAT)}$SUFFIX"

    /** 不是快照文件名时返回 null */
    fun parse(fileName: String): Parsed? {
        if (!fileName.startsWith(PREFIX) || !fileName.endsWith(SUFFIX)) return null
        val body = fileName.removePrefix(PREFIX).removeSuffix(SUFFIX)
        // 时间部分不含下划线，所以最后一个下划线之后就是时间（包名本身可能含下划线）
        val separator = body.lastIndexOf('_')
        if (separator <= 0) return null
        val packageName = body.substring(0, separator)
        val time = try {
            LocalDateTime.parse(body.substring(separator + 1), TIME_FORMAT)
        } catch (_: DateTimeParseException) {
            return null
        }
        return Parsed(packageName, time)
    }
}

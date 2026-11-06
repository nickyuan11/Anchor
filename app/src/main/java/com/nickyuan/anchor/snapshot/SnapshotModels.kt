package com.nickyuan.anchor.snapshot

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/*
 * 页面快照的数据结构，最终导出为 zip 里的 snapshot.json。
 *
 * @Serializable：kotlinx.serialization 的注解，编译期自动生成"对象 <-> JSON"的转换代码，
 * 作用类似 Jackson，但不靠运行时反射，所以更快、也不怕代码混淆。
 */

/** 一份完整的页面快照 */
@Serializable
data class Snapshot(
    /**
     * 快照格式版本，以后改格式时加 1，方便兼容旧快照。
     * @EncodeDefault：即使等于默认值也必须写进 JSON（SnapshotWriter 默认会省略默认值字段）
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault
    val formatVersion: Int = FORMAT_VERSION,
    /** 抓取时间，例如 "2026-11-06T15:30:12" */
    val capturedAt: String,
    val app: AppSnapshot,
    val device: DeviceSnapshot,
    /** 屏幕上所有窗口（App 主窗口、弹窗、状态栏等）及其控件树 */
    val windows: List<WindowSnapshot>,
    /** 这个 App 最近的跳过记录，用来排查"点错了"之类的问题 */
    val recentSkipLogs: List<SkipLogSnapshot>,
    /** zip 里是否带有 screenshot.jpg */
    val hasScreenshot: Boolean,
    /** 服务内部最近的诊断日志（窗口变化、点击尝试、手势结果等），格式版本 2 起提供 */
    val diagnostics: List<String> = emptyList(),
) {
    companion object {
        /** 1：初版；2：增加 diagnostics */
        const val FORMAT_VERSION = 2
    }
}

/** 被抓取的 App */
@Serializable
data class AppSnapshot(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long?,
    /** 当前页面（Activity）完整类名，规则里的 activities 字段就填它 */
    val activity: String?,
    /** 抓取时距离 App 打开过了多少毫秒；不知道时为 null */
    val elapsedSinceLaunchMs: Long?,
)

/** 手机信息：品牌、型号、系统版本，用来区分澎湃 OS、鸿蒙等不同系统 */
@Serializable
data class DeviceSnapshot(
    val manufacturer: String,
    val brand: String,
    val model: String,
    /** Android 版本号，例如 "14" */
    val androidVersion: String,
    /** Android API 级别，例如 34 */
    val sdkInt: Int,
    /** 系统构建号，通常能看出 ROM 名称和版本 */
    val buildDisplay: String,
    val screenWidth: Int,
    val screenHeight: Int,
)

/** 屏幕上的一个窗口 */
@Serializable
data class WindowSnapshot(
    /** 窗口类型：application（App 页面）、system（状态栏等）、input_method（输入法）…… */
    val type: String,
    val title: String?,
    val packageName: String?,
    /** 是否是当前活动窗口（用户正在操作的那个） */
    val isActive: Boolean,
    /** 窗口层级，数字越大越靠上 */
    val layer: Int,
    /** "左,上,右,下"，单位像素 */
    val bounds: String,
    /** 控件树的根节点；拿不到时为 null */
    val root: NodeSnapshot?,
    /** 是否因为控件太多而被截断 */
    val truncated: Boolean = false,
)

/** 控件树里的一个节点 */
@Serializable
data class NodeSnapshot(
    /** 控件类名，例如 android.widget.TextView */
    val className: String? = null,
    val text: String? = null,
    /** 内容描述（content-description） */
    val desc: String? = null,
    /** view id，例如 com.example:id/skip_btn，规则里的 viewId 字段就填它 */
    val viewId: String? = null,
    /** "左,上,右,下"，单位像素 */
    val bounds: String,
    val clickable: Boolean = false,
    val visible: Boolean = true,
    val enabled: Boolean = true,
    val children: List<NodeSnapshot> = emptyList(),
)

/** 一条跳过记录（与数据库里的 SkipLogEntity 对应，只保留排查需要的字段） */
@Serializable
data class SkipLogSnapshot(
    val timestamp: Long,
    val activity: String?,
    val ruleId: String,
    val matchedText: String,
    val clickMethod: String,
    val shakeDetected: Boolean,
    val elapsedSinceLaunchMs: Long,
)

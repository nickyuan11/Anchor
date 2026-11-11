package com.nickyuan.anchor.guide

/** 保活步骤上的按钮要跳到哪个系统页面 */
enum class KeepAliveTarget {
    /** 自启动 / 应用启动管理 */
    AUTO_START,

    /** 省电策略 */
    BATTERY_SAVER,

    /** 本 App 的"应用信息"页 */
    APP_DETAILS,
}

/**
 * 一条保活设置说明。
 *
 * @param id 步骤标识，用来记住用户有没有勾选"已设置"
 * @param target 按钮跳转的页面；null 表示这一步没有可跳转的页面，只有文字说明
 */
data class KeepAliveStep(
    val id: String,
    val title: String,
    val description: String,
    val target: KeepAliveTarget? = null,
    val buttonLabel: String? = null,
)

/**
 * 各品牌的保活设置步骤（design.md 第 7 节的表格）。
 * 这些设置系统不提供查询接口，App 没法知道用户有没有设好，所以每一步让用户自己勾选"已设置"。
 *
 * 文案里的菜单名称来自设计文档，实际名称以测试机界面为准，等朋友截图确认后再修正。
 */
object KeepAliveGuide {

    fun stepsFor(brand: DeviceBrand): List<KeepAliveStep> = when (brand) {
        DeviceBrand.XIAOMI -> listOf(
            KeepAliveStep(
                id = "xiaomi_autostart",
                title = "允许自启动",
                description = "在列表里找到「定海神针」，打开开关。系统清理后台后，服务才能自己重新启动。",
                target = KeepAliveTarget.AUTO_START,
                buttonLabel = "打开自启动管理",
            ),
            KeepAliveStep(
                id = "xiaomi_battery",
                title = "省电策略改为「无限制」",
                description = "默认的「智能限制后台运行」会在锁屏一段时间后关掉服务。",
                target = KeepAliveTarget.BATTERY_SAVER,
                buttonLabel = "打开省电策略",
            ),
            KeepAliveStep(
                id = "xiaomi_lock",
                title = "在最近任务里锁定",
                description = "从屏幕底部上滑并停住，打开最近任务，长按「定海神针」的卡片，点小锁图标。这样一键清理时不会被清掉。",
            ),
        )

        DeviceBrand.HUAWEI -> listOf(
            KeepAliveStep(
                id = "huawei_startup",
                title = "应用启动管理改为「手动管理」",
                description = "找到「定海神针」，关掉「自动管理」，在弹出的窗口里把「允许自启动」「允许关联启动」「允许后台活动」三个开关都打开。",
                target = KeepAliveTarget.AUTO_START,
                buttonLabel = "打开应用启动管理",
            ),
            KeepAliveStep(
                id = "huawei_lock",
                title = "在最近任务里锁定",
                description = "从屏幕底部上滑并停住，打开最近任务，把「定海神针」的卡片往下拉一下，出现小锁图标即可。",
            ),
        )

        DeviceBrand.OTHER -> listOf(
            KeepAliveStep(
                id = "other_background",
                title = "允许后台运行和自启动",
                description = "在应用信息页里找「电池」「自启动」「后台运行」之类的选项，改成允许或不限制。不同品牌的名称不一样。",
                target = KeepAliveTarget.APP_DETAILS,
                buttonLabel = "打开应用信息",
            ),
            KeepAliveStep(
                id = "other_lock",
                title = "在最近任务里锁定",
                description = "打开最近任务，长按或下拉「定海神针」的卡片，把它锁定，避免被一键清理。",
            ),
        )
    }
}

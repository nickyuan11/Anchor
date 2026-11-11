package com.nickyuan.anchor.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import com.nickyuan.anchor.guide.DeviceBrand
import com.nickyuan.anchor.guide.KeepAliveTarget

/**
 * 跳转到各种系统设置页面（design.md 第 7 节"一键跳转"）。
 *
 * 自启动、省电策略这些页面是各厂商自己做的，没有统一的打开方式，只能按"包名 + 页面类名"去试。
 * 不同系统版本的类名可能不一样，所以每个目标准备了几个候选，挨个尝试；
 * 全部失败就退回到本 App 的"应用信息"页（那里一般也能找到对应的入口）。
 */
object SystemSettingsNavigator {

    private const val TAG = "SettingsNavigator"

    /** 本 App 的"应用信息"页（"允许受限制的设置"就在这个页面右上角的菜单里） */
    fun openAppDetails(context: Context): Boolean =
        tryStart(context, listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))))

    /** 系统是否已经对本 App 关闭了电池优化（关闭后，锁屏休眠时不会限制它） */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * 弹出系统对话框，请用户允许本 App 不受电池优化限制。
     * 需要在 AndroidManifest.xml 里声明 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限。
     */
    @SuppressLint("BatteryLife") // 这条 lint 是针对上架应用商店的政策提醒；本 App 不上架，且保活正是核心需求
    fun requestIgnoreBatteryOptimizations(context: Context): Boolean = tryStart(
        context,
        listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)),
            // 个别系统不支持上面的对话框，退而打开"电池优化"列表页，让用户自己找
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        ),
    ) || openAppDetails(context)

    /**
     * 打开保活设置页面。
     * @return true = 打开了对应的专门页面；false = 没找到，已退回到应用信息页
     */
    fun openKeepAlive(context: Context, brand: DeviceBrand, target: KeepAliveTarget): Boolean {
        val candidates = when (target) {
            KeepAliveTarget.APP_DETAILS -> emptyList()
            KeepAliveTarget.AUTO_START -> when (brand) {
                DeviceBrand.XIAOMI -> listOf(
                    component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
                )
                DeviceBrand.HUAWEI -> listOf(
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                    component("com.hihonor.systemmanager", "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                )
                DeviceBrand.OTHER -> emptyList()
            }
            KeepAliveTarget.BATTERY_SAVER -> when (brand) {
                DeviceBrand.XIAOMI -> listOf(
                    component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                        .putExtra("package_name", context.packageName)
                        .putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager).toString()),
                )
                else -> emptyList()
            }
        }
        if (tryStart(context, candidates)) return true
        openAppDetails(context)
        return false
    }

    private fun component(packageName: String, className: String) = Intent().setClassName(packageName, className)

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    /** 挨个尝试，第一个能打开的就算成功 */
    private fun tryStart(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                // 从非 Activity 的 Context 打开页面时必须加 NEW_TASK
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                // 页面不存在（ActivityNotFoundException）或不允许外部打开（SecurityException），试下一个
                Log.i(TAG, "打不开 $intent：${e.javaClass.simpleName}")
            }
        }
        return false
    }
}

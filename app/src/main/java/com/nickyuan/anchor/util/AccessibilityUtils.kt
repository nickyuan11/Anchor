package com.nickyuan.anchor.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.nickyuan.anchor.service.SkipAccessibilityService

/** 无障碍服务相关的小工具 */
object AccessibilityUtils {

    /**
     * 用户是否已在系统设置里打开了本 App 跳过服务的开关。
     * 注意：开关开着不代表服务正在运行（可能被系统杀掉了），是否在运行看 ServiceStatus。
     *
     * 系统把所有已开启的无障碍服务记在一个设置项里，格式是用冒号分隔的"包名/类名"列表。
     */
    fun isEnabledInSettings(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = ComponentName(context, SkipAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    /** 跳转到系统的"无障碍"设置页 */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

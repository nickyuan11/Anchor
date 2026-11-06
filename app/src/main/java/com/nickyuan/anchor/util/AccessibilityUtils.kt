package com.nickyuan.anchor.util

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.nickyuan.anchor.service.SkipAccessibilityService

/** 无障碍服务相关的小工具 */
object AccessibilityUtils {

    /** 用户是否已在系统设置里开启了本 App 的跳过服务 */
    fun isSkipServiceEnabled(context: Context): Boolean {
        val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                val info = it.resolveInfo.serviceInfo
                info.packageName == context.packageName &&
                    info.name == SkipAccessibilityService::class.java.name
            }
    }

    /** 跳转到系统的"无障碍"设置页 */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

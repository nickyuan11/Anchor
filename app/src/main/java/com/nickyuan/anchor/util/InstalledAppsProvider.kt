package com.nickyuan.anchor.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 手机上一个有桌面图标的 App */
data class InstalledApp(val packageName: String, val label: String)

/**
 * 查询手机上有桌面图标的 App（白名单页使用）。
 * 需要 AndroidManifest.xml 里的 <queries> 声明，否则 Android 11 起查不到其他 App。
 */
@Singleton
class InstalledAppsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun launcherApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        resolved
            .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .distinctBy { it.packageName } // 一个 App 可能有多个桌面图标
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label }
    }

    /** App 图标，转成指定像素大小的位图；取不到时返回 null */
    suspend fun icon(packageName: String, sizePx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(sizePx, sizePx) }.getOrNull()
    }
}

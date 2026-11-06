package com.nickyuan.anchor.util

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 把包名翻译成 App 名称，例如 "com.tencent.mm" → "微信"。
 * 查询结果会缓存起来，避免重复查询。查不到（App 已卸载等）时直接返回包名。
 *
 * PackageManager：系统的"应用管理器"，可以查询手机上安装的 App 信息。
 */
@Singleton
class AppLabelProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cache = ConcurrentHashMap<String, String>()

    fun labelOf(packageName: String): String = cache.getOrPut(packageName) {
        val pm = context.packageManager
        try {
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}

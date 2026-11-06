package com.nickyuan.anchor.rule

import android.content.Context
import android.util.Log
import com.nickyuan.anchor.rule.model.AppRules
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 规则仓库：加载规则文件，按包名建索引，供无障碍服务查询（design.md 第 2 节"规则在启动时预先加载到内存，按包名直接查表"）。
 *
 * 规则来源（后面的按 id 覆盖前面的）：
 * 1. assets/rules/builtin.json：内置规则，打包在 APK 里；
 * 2. assets/rules/debug.json：仅 debug 包有，给模拟广告页用的测试规则；
 * 3. 用户规则：v1.1 做导入功能时加入。
 *
 * assets：APK 里的只读文件目录，对应源码里的 src/main/assets/，用 context.assets 读取。
 */
@Singleton
class RuleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 包名 → 规则。加载后不再改变，多线程读取是安全的 */
    @Volatile
    private var byPackage: Map<String, AppRules> = emptyMap()

    /** 加载时发现的问题（不合法的规则等），方便排查 */
    @Volatile
    var problems: List<String> = emptyList()
        private set

    @Volatile
    private var loaded = false

    /** 还没加载过就加载一次（界面进程里服务可能没在运行，需要自己加载） */
    fun ensureLoaded() {
        if (!loaded) load()
    }

    /** 读取并合并所有规则文件。文件很小，可以在服务启动时调用 */
    fun load() {
        val allProblems = mutableListOf<String>()
        var merged = emptyList<AppRules>()
        for (path in RULE_FILES) {
            val text = readAsset(path) ?: continue
            try {
                val (apps, problems) = RuleParser.sanitize(RuleParser.parse(text))
                merged = RuleParser.merge(merged, apps)
                allProblems += problems.map { "$path：$it" }
            } catch (e: Exception) {
                // 整个文件格式错误：跳过这个文件，不影响其他文件
                allProblems += "$path 解析失败：${e.message}"
            }
        }
        byPackage = merged.associateBy { it.packageName }
        problems = allProblems
        loaded = true
        allProblems.forEach { Log.w(TAG, it) }
        Log.i(TAG, "已加载 ${byPackage.size} 个 App 的规则")
    }

    fun rulesFor(packageName: String): AppRules? = byPackage[packageName]

    /** 有规则的 App 列表（设置页展示用） */
    fun allApps(): List<AppRules> = byPackage.values.toList()

    private fun readAsset(path: String): String? = try {
        context.assets.open(path).bufferedReader().use { it.readText() }
    } catch (_: FileNotFoundException) {
        null // debug.json 只在 debug 包里有，release 包找不到是正常的
    }

    private companion object {
        const val TAG = "RuleRepository"
        val RULE_FILES = listOf("rules/builtin.json", "rules/debug.json")
    }
}

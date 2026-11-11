package com.nickyuan.anchor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore：Android 官方的设置存储，可以理解为一个持久化到文件里的键值对 Map。
 * 读出来的是 Flow：设置一改，所有订阅者（页面、无障碍服务）都会自动收到新值。
 *
 * by preferencesDataStore(...)：Kotlin 的"属性委托"，保证整个 App 只创建一个 DataStore 实例。
 * 必须写在文件顶层，不能写在类里。
 */
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App 设置的读写入口 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dataStore = context.settingsDataStore

    /** 是否显示悬浮的"抓取页面快照"按钮，默认关闭 */
    val snapshotButtonEnabled: Flow<Boolean> =
        dataStore.data.map { it[KEY_SNAPSHOT_BUTTON] ?: false }

    suspend fun setSnapshotButtonEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_SNAPSHOT_BUTTON] = enabled }
    }

    /** 通用关键词兜底是否开启，默认开启（design.md 第 2 节"这个功能用户可以关闭"） */
    val fallbackEnabled: Flow<Boolean> =
        dataStore.data.map { it[KEY_FALLBACK] ?: true }

    suspend fun setFallbackEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_FALLBACK] = enabled }
    }

    /** 白名单：这些 App 完全不处理（包名集合） */
    val whitelist: Flow<Set<String>> =
        dataStore.data.map { it[KEY_WHITELIST] ?: emptySet() }

    suspend fun setWhitelisted(packageName: String, whitelisted: Boolean) {
        dataStore.edit {
            val current = it[KEY_WHITELIST] ?: emptySet()
            it[KEY_WHITELIST] = if (whitelisted) current + packageName else current - packageName
        }
    }

    // ---------- 开启与保活引导（M4b） ----------

    /** 是否已经自动弹出过一次开启引导 */
    val onboardingShown: Flow<Boolean> =
        dataStore.data.map { it[KEY_ONBOARDING_SHOWN] ?: false }

    suspend fun setOnboardingShown() {
        dataStore.edit { it[KEY_ONBOARDING_SHOWN] = true }
    }

    /** 用户在保活引导里勾选了"已设置"的步骤 id */
    val guideChecked: Flow<Set<String>> =
        dataStore.data.map { it[KEY_GUIDE_CHECKED] ?: emptySet() }

    suspend fun setGuideChecked(stepId: String, checked: Boolean) {
        dataStore.edit {
            val current = it[KEY_GUIDE_CHECKED] ?: emptySet()
            it[KEY_GUIDE_CHECKED] = if (checked) current + stepId else current - stepId
        }
    }

    /** 跳过服务最近几次启动的时间，从旧到新（见 ServiceStartHistory） */
    val serviceStarts: Flow<List<Long>> =
        dataStore.data.map { ServiceStartHistory.parse(it[KEY_SERVICE_STARTS]) }

    /** 跳过服务累计启动了多少次 */
    val serviceStartCount: Flow<Int> =
        dataStore.data.map { it[KEY_SERVICE_START_COUNT] ?: 0 }

    /** 服务每次启动时调用。返回这是第几次启动 */
    suspend fun recordServiceStart(startedAt: Long): Int {
        var count = 0
        dataStore.edit {
            it[KEY_SERVICE_STARTS] = ServiceStartHistory.append(it[KEY_SERVICE_STARTS], startedAt)
            count = (it[KEY_SERVICE_START_COUNT] ?: 0) + 1
            it[KEY_SERVICE_START_COUNT] = count
        }
        return count
    }

    private companion object {
        val KEY_ONBOARDING_SHOWN = booleanPreferencesKey("onboarding_shown")
        val KEY_GUIDE_CHECKED = stringSetPreferencesKey("guide_checked")
        val KEY_SERVICE_STARTS = stringPreferencesKey("service_starts")
        val KEY_SERVICE_START_COUNT = intPreferencesKey("service_start_count")
        val KEY_SNAPSHOT_BUTTON = booleanPreferencesKey("snapshot_button_enabled")
        val KEY_FALLBACK = booleanPreferencesKey("fallback_enabled")
        val KEY_WHITELIST = stringSetPreferencesKey("whitelist")
    }
}

package com.nickyuan.anchor.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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

    private companion object {
        val KEY_SNAPSHOT_BUTTON = booleanPreferencesKey("snapshot_button_enabled")
    }
}

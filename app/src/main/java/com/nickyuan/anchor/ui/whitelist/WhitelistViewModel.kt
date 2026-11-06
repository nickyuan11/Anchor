package com.nickyuan.anchor.ui.whitelist

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickyuan.anchor.data.SettingsRepository
import com.nickyuan.anchor.util.InstalledApp
import com.nickyuan.anchor.util.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 白名单页的一行 */
data class WhitelistItem(val app: InstalledApp, val whitelisted: Boolean)

@HiltViewModel
class WhitelistViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val installedAppsProvider: InstalledAppsProvider,
) : ViewModel() {

    private val apps = MutableStateFlow<List<InstalledApp>?>(null) // null = 还在加载
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * 列表 = 已安装 App × 白名单 × 搜索词。combine：任何一个变化，就重新计算列表。
     * 白名单里的排在前面；null 表示还在加载。
     */
    val items: StateFlow<List<WhitelistItem>?> = combine(apps, settingsRepository.whitelist, _query) { all, whitelist, q ->
        all
            ?.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
            ?.map { WhitelistItem(it, it.packageName in whitelist) }
            ?.sortedBy { !it.whitelisted }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { apps.value = installedAppsProvider.launcherApps() }
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    fun setWhitelisted(packageName: String, whitelisted: Boolean) {
        viewModelScope.launch { settingsRepository.setWhitelisted(packageName, whitelisted) }
    }

    suspend fun icon(packageName: String, sizePx: Int): Bitmap? = installedAppsProvider.icon(packageName, sizePx)
}

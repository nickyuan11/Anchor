package com.nickyuan.anchor.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickyuan.anchor.data.SettingsRepository
import com.nickyuan.anchor.rule.RuleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ruleRepository: RuleRepository,
) : ViewModel() {

    val fallbackEnabled: StateFlow<Boolean> = settingsRepository.fallbackEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val whitelistCount: StateFlow<Int> = settingsRepository.whitelist
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _ruleApps = MutableStateFlow<List<String>>(emptyList())

    /** 有内置规则的 App 名称 */
    val ruleApps: StateFlow<List<String>> = _ruleApps.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            ruleRepository.ensureLoaded()
            _ruleApps.value = ruleRepository.allApps().filter { it.enabled }.map { it.name ?: it.packageName }
        }
    }

    fun setFallbackEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setFallbackEnabled(enabled) }
    }
}

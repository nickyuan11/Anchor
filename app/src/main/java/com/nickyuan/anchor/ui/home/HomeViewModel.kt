package com.nickyuan.anchor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickyuan.anchor.data.SettingsRepository
import com.nickyuan.anchor.service.ServiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 主页的 ViewModel：服务运行情况、"显示快照按钮"开关、是否需要弹出开启引导 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    serviceStatus: ServiceStatus,
) : ViewModel() {

    /** 服务此刻是否真的在运行 */
    val serviceConnected: StateFlow<Boolean> = serviceStatus.connected

    /** 服务最近几次启动的时间，从旧到新 */
    val serviceStarts: StateFlow<List<Long>> = settingsRepository.serviceStarts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val serviceStartCount: StateFlow<Int> = settingsRepository.serviceStartCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 是否已经自动弹出过开启引导；null 表示设置还没读出来 */
    val onboardingShown: StateFlow<Boolean?> = settingsRepository.onboardingShown
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun markOnboardingShown() {
        viewModelScope.launch { settingsRepository.setOnboardingShown() }
    }

    val snapshotButtonEnabled: StateFlow<Boolean> = settingsRepository.snapshotButtonEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setSnapshotButtonEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSnapshotButtonEnabled(enabled) }
    }
}

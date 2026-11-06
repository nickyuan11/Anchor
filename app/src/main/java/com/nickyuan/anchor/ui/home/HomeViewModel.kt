package com.nickyuan.anchor.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickyuan.anchor.data.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 主页的 ViewModel：读写"显示快照按钮"开关 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val snapshotButtonEnabled: StateFlow<Boolean> = settingsRepository.snapshotButtonEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setSnapshotButtonEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSnapshotButtonEnabled(enabled) }
    }
}

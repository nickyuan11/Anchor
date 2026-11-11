package com.nickyuan.anchor.ui.guide

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickyuan.anchor.data.SettingsRepository
import com.nickyuan.anchor.guide.DeviceBrand
import com.nickyuan.anchor.guide.KeepAliveGuide
import com.nickyuan.anchor.guide.KeepAliveStep
import com.nickyuan.anchor.service.ServiceStatus
import com.nickyuan.anchor.util.DiagnosticsLog
import com.nickyuan.anchor.util.SystemSettingsNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GuideViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    serviceStatus: ServiceStatus,
    private val diagnostics: DiagnosticsLog,
) : ViewModel() {

    /** 根据系统提供的厂商、品牌名识别手机品牌 */
    val brand: DeviceBrand = DeviceBrand.detect(Build.MANUFACTURER, Build.BRAND)

    val keepAliveSteps: List<KeepAliveStep> = KeepAliveGuide.stepsFor(brand)

    /** 服务是否真的在运行 */
    val serviceConnected: StateFlow<Boolean> = serviceStatus.connected

    /** 用户勾选了"已设置"的保活步骤 */
    val checked: StateFlow<Set<String>> = settingsRepository.guideChecked
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** 把跳转尝试的结果记进诊断日志（随页面快照导出） */
    fun logJump(stepId: String, result: SystemSettingsNavigator.JumpResult) {
        diagnostics.add("保活跳转 $stepId（${Build.MANUFACTURER} ${Build.DISPLAY}）：${result.attempts.joinToString("；").ifEmpty { "无候选" }}")
    }

    fun setChecked(stepId: String, checked: Boolean) {
        viewModelScope.launch { settingsRepository.setGuideChecked(stepId, checked) }
    }
}

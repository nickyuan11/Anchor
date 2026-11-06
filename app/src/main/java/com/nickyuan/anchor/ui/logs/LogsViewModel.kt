package com.nickyuan.anchor.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickyuan.anchor.data.SkipLogRepository
import com.nickyuan.anchor.data.db.SkipLogEntity
import com.nickyuan.anchor.service.ClickMethod
import com.nickyuan.anchor.util.AppLabelProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** 记录页上一行要显示的内容（已经整理成界面直接能用的格式） */
data class SkipLogItem(
    val id: Long,
    val time: String,
    val appName: String,
    val pageName: String?,
    val matchedText: String,
    val clickMethod: String,
    val shakeDetected: Boolean,
    val elapsedMs: Long,
)

/**
 * 记录页的 ViewModel：负责给页面准备数据、处理页面上的操作。
 *
 * ViewModel 的生命周期比页面长：手机旋转屏幕时页面会重建，但 ViewModel 不会，数据也就不会丢。
 * @HiltViewModel：由 Hilt 创建，构造函数参数自动注入。
 */
@HiltViewModel
class LogsViewModel @Inject constructor(
    private val repository: SkipLogRepository,
    private val appLabelProvider: AppLabelProvider,
) : ViewModel() {

    /**
     * 记录列表。数据库一变化，这里就自动产生新列表，页面随之刷新。
     * stateIn：把 Flow 转成 StateFlow，页面不在前台超过 5 秒就暂停订阅，节省资源。
     */
    val logs: StateFlow<List<SkipLogItem>> = repository.observeRecent()
        .map { entities -> entities.map { it.toItem() } }
        .flowOn(Dispatchers.IO) // 查询 App 名称会访问系统服务，放到 IO 线程
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun clearAll() {
        // viewModelScope：ViewModel 自带的协程作用域，ViewModel 销毁时自动取消
        viewModelScope.launch { repository.clear() }
    }

    private fun SkipLogEntity.toItem(): SkipLogItem {
        val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        return SkipLogItem(
            id = id,
            time = format.format(Date(timestamp)),
            appName = appLabelProvider.labelOf(packageName),
            pageName = activityName?.substringAfterLast('.'),
            matchedText = matchedText,
            clickMethod = runCatching { ClickMethod.valueOf(clickMethod).label }.getOrDefault(clickMethod),
            shakeDetected = shakeDetected,
            elapsedMs = elapsedSinceLaunchMs,
        )
    }
}

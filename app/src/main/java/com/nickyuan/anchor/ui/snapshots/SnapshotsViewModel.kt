package com.nickyuan.anchor.ui.snapshots

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nickyuan.anchor.snapshot.SnapshotEntry
import com.nickyuan.anchor.snapshot.SnapshotRepository
import com.nickyuan.anchor.util.AppLabelProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** 快照列表的一行 */
data class SnapshotItem(
    val entry: SnapshotEntry,
    val appName: String,
    val time: String,
    val size: String,
)

@HiltViewModel
class SnapshotsViewModel @Inject constructor(
    private val repository: SnapshotRepository,
    private val appLabelProvider: AppLabelProvider,
) : ViewModel() {

    private val timeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")

    val items: StateFlow<List<SnapshotItem>> = repository.entries
        .map { entries ->
            entries.map {
                SnapshotItem(
                    entry = it,
                    appName = appLabelProvider.labelOf(it.packageName),
                    time = it.capturedAt.format(timeFormat),
                    size = formatSize(it.sizeBytes),
                )
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // 打开页面时扫描一次目录（App 刚启动时列表还是空的）
        viewModelScope.launch { repository.refresh() }
    }

    fun delete(item: SnapshotItem) {
        viewModelScope.launch { repository.delete(item.entry) }
    }

    suspend fun loadThumbnail(item: SnapshotItem): Bitmap? = repository.loadThumbnail(item.entry)

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        else -> "${(bytes + 1023) / 1024} KB"
    }
}

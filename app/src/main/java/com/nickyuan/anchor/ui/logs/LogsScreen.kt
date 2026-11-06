package com.nickyuan.anchor.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickyuan.anchor.ui.theme.AnchorTheme

/**
 * 跳过记录页。
 * hiltViewModel()：拿到由 Hilt 创建的 LogsViewModel（同一个页面多次调用拿到的是同一个实例）。
 */
@Composable
fun LogsScreen(onBack: () -> Unit, viewModel: LogsViewModel = hiltViewModel()) {
    // collectAsStateWithLifecycle：订阅 StateFlow，页面在后台时自动暂停
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    LogsContent(logs = logs, onBack = onBack, onClearAll = viewModel::clearAll)
}

@OptIn(ExperimentalMaterial3Api::class) // TopAppBar 目前标记为实验性 API，需要显式声明
@Composable
private fun LogsContent(
    logs: List<SkipLogItem>,
    onBack: () -> Unit,
    onClearAll: () -> Unit,
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("跳过记录") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    if (logs.isNotEmpty()) {
                        TextButton(onClick = { showClearDialog = true }) { Text("清空") }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (logs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("还没有跳过记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // LazyColumn：只渲染屏幕上看得见的行，列表再长也不卡（类似 RecyclerView）
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(logs, key = { it.id }) { item -> LogRow(item) }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空全部记录？") },
            text = { Text("清空后无法恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    onClearAll()
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun LogRow(item: SkipLogItem) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.appName + if (item.shakeDetected) "  📳" else "",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Text(item.time, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "「${item.matchedText}」· ${item.clickMethod} · 打开后 ${item.elapsedMs} ms",
                style = MaterialTheme.typography.bodyMedium,
            )
            item.pageName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun LogsContentPreview() {
    AnchorTheme {
        LogsContent(
            logs = listOf(
                SkipLogItem(1, "11-06 15:30:12", "示例 App", "SplashActivity", "跳过 5", "点击控件", true, 420),
                SkipLogItem(2, "11-06 15:28:40", "另一个 App", null, "Skip", "模拟手势", false, 1310),
            ),
            onBack = {},
            onClearAll = {},
        )
    }
}

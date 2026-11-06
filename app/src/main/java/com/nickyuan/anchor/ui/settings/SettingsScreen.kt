package com.nickyuan.anchor.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 设置页：通用兜底开关、白名单入口、内置规则列表 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenWhitelist: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val fallbackEnabled by viewModel.fallbackEnabled.collectAsStateWithLifecycle()
    val whitelistCount by viewModel.whitelistCount.collectAsStateWithLifecycle()
    val ruleApps by viewModel.ruleApps.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("通用关键词兜底", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "没有专属规则的 App，打开后 5 秒内自动点击角落里的「跳过」按钮。" +
                                "如果遇到误点，可以关闭，只使用专属规则。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = fallbackEnabled, onCheckedChange = viewModel::setFallbackEnabled)
                }
            }
            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("应用白名单", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "白名单里的 App 完全不处理。当前 $whitelistCount 个。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onOpenWhitelist, modifier = Modifier.fillMaxWidth()) { Text("管理白名单") }
                }
            }
            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("内置规则", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (ruleApps.isEmpty()) "暂无" else ruleApps.joinToString("、"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "这些 App 按专属规则跳过，比通用兜底更准、更快。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

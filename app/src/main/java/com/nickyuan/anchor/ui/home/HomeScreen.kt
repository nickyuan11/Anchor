package com.nickyuan.anchor.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickyuan.anchor.R
import com.nickyuan.anchor.debug.DebugPanel
import com.nickyuan.anchor.ui.theme.AnchorTheme
import com.nickyuan.anchor.util.AccessibilityUtils

/**
 * 主页：显示跳过服务是否在运行，提供"去开启"和"跳过记录"入口。
 */
@Composable
fun HomeScreen(
    onOpenLogs: () -> Unit,
    onOpenSnapshots: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snapshotButtonEnabled by viewModel.snapshotButtonEnabled.collectAsStateWithLifecycle()
    var serviceEnabled by remember { mutableStateOf(AccessibilityUtils.isSkipServiceEnabled(context)) }

    // 每次页面回到前台（比如从系统设置返回）都重新检查一次服务状态
    LifecycleResumeEffect(Unit) {
        serviceEnabled = AccessibilityUtils.isSkipServiceEnabled(context)
        onPauseOrDispose { }
    }

    HomeContent(
        serviceEnabled = serviceEnabled,
        onEnableService = { AccessibilityUtils.openAccessibilitySettings(context) },
        onOpenLogs = onOpenLogs,
        snapshotButtonEnabled = snapshotButtonEnabled,
        onSnapshotButtonChange = viewModel::setSnapshotButtonEnabled,
        onOpenSnapshots = onOpenSnapshots,
    )
}

/** 主页界面本身。和状态检查分开写，方便在 Android Studio 里预览 */
@Composable
private fun HomeContent(
    serviceEnabled: Boolean,
    onEnableService: () -> Unit,
    onOpenLogs: () -> Unit,
    snapshotButtonEnabled: Boolean,
    onSnapshotButtonChange: (Boolean) -> Unit,
    onOpenSnapshots: () -> Unit,
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(24.dp))

            ServiceStatusCard(serviceEnabled, onEnableService, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            OutlinedButton(onClick = onOpenLogs, modifier = Modifier.fillMaxWidth()) {
                Text("跳过记录")
            }
            Spacer(Modifier.height(16.dp))

            SnapshotCard(
                serviceEnabled = serviceEnabled,
                buttonEnabled = snapshotButtonEnabled,
                onButtonChange = onSnapshotButtonChange,
                onOpenSnapshots = onOpenSnapshots,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))

            // 调试面板：debug 包里显示模拟广告入口和测试结果，release 包里是空的
            DebugPanel(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ServiceStatusCard(
    enabled: Boolean,
    onEnableService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = if (enabled) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    }
    Card(modifier = modifier, colors = colors) {
        Column(Modifier.padding(16.dp)) {
            if (enabled) {
                Text("✅ 跳过服务运行中", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("打开 App 时会自动点掉开屏广告的「跳过」按钮。", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("⚠️ 跳过服务未开启", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "需要在系统的「无障碍」设置里，找到「定海神针 · 开屏跳过」并开启。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onEnableService) { Text("去开启") }
            }
        }
    }
}

/** 页面快照：悬浮按钮开关 + 快照列表入口 */
@Composable
private fun SnapshotCard(
    serviceEnabled: Boolean,
    buttonEnabled: Boolean,
    onButtonChange: (Boolean) -> Unit,
    onOpenSnapshots: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("显示快照按钮", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (serviceEnabled) "屏幕边上会出现 📷，遇到没跳过的广告点一下" else "需要先开启跳过服务",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = buttonEnabled, onCheckedChange = onButtonChange)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "快照包含当时屏幕上的文字和截图，只保存在本机，只有你主动分享时才会发出去。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenSnapshots, modifier = Modifier.fillMaxWidth()) {
                Text("页面快照")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    AnchorTheme {
        HomeContent(
            serviceEnabled = false,
            onEnableService = {},
            onOpenLogs = {},
            snapshotButtonEnabled = false,
            onSnapshotButtonChange = {},
            onOpenSnapshots = {},
        )
    }
}

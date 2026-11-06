package com.nickyuan.anchor.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nickyuan.anchor.R
import com.nickyuan.anchor.debug.DebugPanel
import com.nickyuan.anchor.ui.theme.AnchorTheme
import com.nickyuan.anchor.util.AccessibilityUtils

/**
 * 主页：显示跳过服务是否在运行，提供"去开启"和"跳过记录"入口。
 */
@Composable
fun HomeScreen(onOpenLogs: () -> Unit) {
    val context = LocalContext.current
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
    )
}

/** 主页界面本身。和状态检查分开写，方便在 Android Studio 里预览 */
@Composable
private fun HomeContent(
    serviceEnabled: Boolean,
    onEnableService: () -> Unit,
    onOpenLogs: () -> Unit,
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

@Preview(showBackground = true)
@Composable
private fun HomeContentPreview() {
    AnchorTheme {
        HomeContent(serviceEnabled = false, onEnableService = {}, onOpenLogs = {})
    }
}

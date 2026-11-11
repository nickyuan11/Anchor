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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.nickyuan.anchor.util.DurationFormat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 跳过服务的三种状态 */
private enum class ServiceState {
    /** 正在运行 */
    RUNNING,

    /** 系统设置里的开关开着，但服务没有在运行（多半是被系统杀掉了） */
    ENABLED_NOT_RUNNING,

    /** 没有开启 */
    DISABLED,
}

/**
 * 主页：显示跳过服务的运行情况，提供各功能入口。
 */
@Composable
fun HomeScreen(
    onOpenLogs: () -> Unit,
    onOpenSnapshots: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snapshotButtonEnabled by viewModel.snapshotButtonEnabled.collectAsStateWithLifecycle()
    val connected by viewModel.serviceConnected.collectAsStateWithLifecycle()
    val starts by viewModel.serviceStarts.collectAsStateWithLifecycle()
    val startCount by viewModel.serviceStartCount.collectAsStateWithLifecycle()
    val onboardingShown by viewModel.onboardingShown.collectAsStateWithLifecycle()
    var enabledInSettings by remember { mutableStateOf(AccessibilityUtils.isEnabledInSettings(context)) }

    // 每次页面回到前台（比如从系统设置返回）都重新检查一次开关状态
    LifecycleResumeEffect(Unit) {
        enabledInSettings = AccessibilityUtils.isEnabledInSettings(context)
        onPauseOrDispose { }
    }

    // App 刚启动的一两秒内，系统可能还没来得及连上服务，这时先不急着报"没有在运行"
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(2_000)
        settled = true
    }
    val state = when {
        connected -> ServiceState.RUNNING
        enabledInSettings -> if (settled) ServiceState.ENABLED_NOT_RUNNING else ServiceState.RUNNING
        else -> ServiceState.DISABLED
    }

    // 第一次打开 App 且服务还没开启：自动进入开启引导（只自动弹一次）
    LaunchedEffect(onboardingShown) {
        if (onboardingShown == false) {
            viewModel.markOnboardingShown()
            if (!AccessibilityUtils.isEnabledInSettings(context)) onOpenGuide()
        }
    }

    // 当前时间，每 30 秒刷新一次，用来更新"已连续运行多久"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }

    HomeContent(
        state = state,
        now = now,
        serviceStarts = starts,
        serviceStartCount = startCount,
        onEnableService = { AccessibilityUtils.openAccessibilitySettings(context) },
        onOpenGuide = onOpenGuide,
        onOpenLogs = onOpenLogs,
        snapshotButtonEnabled = snapshotButtonEnabled,
        onSnapshotButtonChange = viewModel::setSnapshotButtonEnabled,
        onOpenSnapshots = onOpenSnapshots,
        onOpenSettings = onOpenSettings,
    )
}

/** 主页界面本身。和状态检查分开写，方便在 Android Studio 里预览 */
@Composable
private fun HomeContent(
    state: ServiceState,
    now: Long,
    serviceStarts: List<Long>,
    serviceStartCount: Int,
    onEnableService: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenLogs: () -> Unit,
    snapshotButtonEnabled: Boolean,
    onSnapshotButtonChange: (Boolean) -> Unit,
    onOpenSnapshots: () -> Unit,
    onOpenSettings: () -> Unit,
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

            ServiceStatusCard(state, now, serviceStarts, serviceStartCount, onEnableService, Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            OutlinedButton(onClick = onOpenGuide, modifier = Modifier.fillMaxWidth()) {
                Text("开启与保活引导")
            }
            OutlinedButton(onClick = onOpenLogs, modifier = Modifier.fillMaxWidth()) {
                Text("跳过记录")
            }
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("设置（兜底开关、白名单）")
            }
            Spacer(Modifier.height(16.dp))

            SnapshotCard(
                serviceEnabled = state == ServiceState.RUNNING,
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
    state: ServiceState,
    now: Long,
    serviceStarts: List<Long>,
    serviceStartCount: Int,
    onEnableService: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = if (state == ServiceState.RUNNING) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    }
    val timeFormat = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    Card(modifier = modifier, colors = colors) {
        Column(Modifier.padding(16.dp)) {
            when (state) {
                ServiceState.RUNNING -> {
                    Text("✅ 跳过服务运行中", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("打开 App 时会自动点掉开屏广告的「跳过」按钮。", style = MaterialTheme.typography.bodyMedium)
                    // 运行时长：从最近一次启动算起
                    serviceStarts.lastOrNull()?.let { startedAt ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "已连续运行 ${DurationFormat.humanize(now - startedAt)}（${timeFormat.format(Date(startedAt))} 启动）",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                ServiceState.ENABLED_NOT_RUNNING -> {
                    Text("⚠️ 服务已开启，但没有在运行", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "可能被系统关掉了。请到「无障碍」设置里把「定海神针 · 开屏跳过」的开关关掉再重新打开，" +
                            "并按「开启与保活引导」检查保活设置。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onEnableService) { Text("去无障碍设置") }
                }

                ServiceState.DISABLED -> {
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

            // 最近几次启动的时间：隔了一晚还是同一次启动，说明整晚没被杀过
            if (serviceStarts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "累计启动 $serviceStartCount 次，最近：" +
                        serviceStarts.takeLast(4).reversed().joinToString("、") { timeFormat.format(Date(it)) },
                    style = MaterialTheme.typography.bodySmall,
                )
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
            state = ServiceState.RUNNING,
            now = 1_800_000_000_000,
            serviceStarts = listOf(1_800_000_000_000 - 9 * 3_600_000L),
            serviceStartCount = 3,
            onEnableService = {},
            onOpenGuide = {},
            onOpenLogs = {},
            snapshotButtonEnabled = false,
            onSnapshotButtonChange = {},
            onOpenSnapshots = {},
            onOpenSettings = {},
        )
    }
}

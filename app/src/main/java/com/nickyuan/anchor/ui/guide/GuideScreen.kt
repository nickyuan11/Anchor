package com.nickyuan.anchor.ui.guide

import android.os.Build
import android.widget.Toast
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nickyuan.anchor.util.AccessibilityUtils
import com.nickyuan.anchor.util.SystemSettingsNavigator

/**
 * 开启与保活引导（design.md 第 3、7 节）。
 *
 * 分两部分：
 * 1. 开启步骤：能自动检测是否完成的，直接显示"已完成 / 未完成"；
 * 2. 保活设置：按手机品牌显示。这些设置系统不让 App 查询，只能让用户自己勾选"已设置"。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit, viewModel: GuideViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val connected by viewModel.serviceConnected.collectAsStateWithLifecycle()
    val checked by viewModel.checked.collectAsStateWithLifecycle()

    // 从系统设置返回时重新检查这两项状态
    var enabledInSettings by remember { mutableStateOf(AccessibilityUtils.isEnabledInSettings(context)) }
    var batteryIgnored by remember { mutableStateOf(SystemSettingsNavigator.isIgnoringBatteryOptimizations(context)) }
    LifecycleResumeEffect(Unit) {
        enabledInSettings = AccessibilityUtils.isEnabledInSettings(context)
        batteryIgnored = SystemSettingsNavigator.isIgnoringBatteryOptimizations(context)
        onPauseOrDispose { }
    }
    val serviceOn = connected || enabledInSettings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开启与保活引导") },
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
            SectionTitle("一、开启跳过服务")

            var number = 1
            // Android 13（API 33）起，不是从应用商店安装的 App 默认不能开启无障碍服务
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                StepCard(
                    number = number++,
                    title = "允许受限制的设置",
                    description = "如果下一步里的开关是灰色的，或者点了提示「受限制的设置」：\n" +
                        "先在无障碍设置里点一次本服务，看到提示后点确定；\n" +
                        "再点下面的按钮打开应用信息页，点右上角的 ⋮，选「允许受限制的设置」。\n" +
                        "开关能正常打开的话，跳过这一步。",
                    status = if (serviceOn) "✅ 不需要了" else "视情况",
                    buttonLabel = "打开应用信息",
                    onClick = { SystemSettingsNavigator.openAppDetails(context) },
                )
            }
            StepCard(
                number = number++,
                title = "开启无障碍服务",
                description = "在列表里找到「定海神针 · 开屏跳过」（可能在「已下载的应用」或「更多已下载的服务」里），打开开关并确认。",
                status = if (serviceOn) "✅ 已完成" else "❌ 未完成",
                buttonLabel = if (serviceOn) null else "去开启",
                onClick = { AccessibilityUtils.openAccessibilitySettings(context) },
            )
            StepCard(
                number = number,
                title = "允许忽略电池优化",
                description = "点按钮后系统会弹出确认框，选「允许」。这样手机锁屏休眠时不会限制本服务。",
                status = if (batteryIgnored) "✅ 已完成" else "❌ 未完成",
                buttonLabel = if (batteryIgnored) null else "去允许",
                onClick = { SystemSettingsNavigator.requestIgnoreBatteryOptimizations(context) },
            )

            Spacer(Modifier.height(16.dp))
            SectionTitle("二、防止被系统关掉")
            Text(
                "检测到你的手机是：${viewModel.brand.label}。\n" +
                    "下面的设置系统不让 App 读取，设好后请自己打勾。菜单名称可能和你手机上略有不同。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            viewModel.keepAliveSteps.forEachIndexed { index, step ->
                StepCard(
                    number = index + 1,
                    title = step.title,
                    description = step.description,
                    status = null,
                    buttonLabel = step.buttonLabel,
                    onClick = {
                        val target = step.target ?: return@StepCard
                        val opened = SystemSettingsNavigator.openKeepAlive(context, viewModel.brand, target)
                        if (!opened) {
                            // Toast：屏幕底部短暂出现的一行提示
                            Toast.makeText(context, "没找到对应页面，已打开应用信息页，请在里面找找", Toast.LENGTH_LONG).show()
                        }
                    },
                    checked = step.id in checked,
                    onCheckedChange = { viewModel.setChecked(step.id, it) },
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "设好之后，可以在主页看到服务连续运行了多久、最近是什么时候启动的。" +
                    "如果早上发现它在夜里重新启动过，或者显示没有在运行，说明还有设置没到位。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
}

/**
 * 一个步骤卡片。
 *
 * @param status 自动检测出的状态文字；null 表示检测不了
 * @param buttonLabel 跳转按钮的文字；null 表示不显示按钮
 * @param checked 用户手动勾选的"已设置"；null 表示这一步不需要手动勾选
 */
@Composable
private fun StepCard(
    number: Int,
    title: String,
    description: String,
    status: String?,
    buttonLabel: String?,
    onClick: () -> Unit,
    checked: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit = {},
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$number. $title", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (status != null) Text(status, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (buttonLabel != null || checked != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (buttonLabel != null) OutlinedButton(onClick = onClick) { Text(buttonLabel) }
                    Spacer(Modifier.weight(1f))
                    if (checked != null) {
                        Text("已设置", style = MaterialTheme.typography.bodySmall)
                        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                    }
                }
            }
        }
    }
}

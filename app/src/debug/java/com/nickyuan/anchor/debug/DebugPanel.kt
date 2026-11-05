package com.nickyuan.anchor.debug

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 主页上的调试面板（debug 版）：显示最近一次测试结果，并提供"打开模拟广告"按钮。
 *
 * src/release/ 下有一个同名、同签名的空实现。主页代码（src/main）只管调用 DebugPanel()，
 * 打 debug 包时用这个文件，打 release 包时用空的那个——正式版里就不会出现调试入口。
 */
@Composable
fun DebugPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // collectAsState：订阅 StateFlow，结果一更新，这里的界面自动刷新
    val last by FakeAdResultStore.last.collectAsState()

    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text("调试工具（仅 debug 包）", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = last?.describe() ?: "还没有测试记录",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                context.startActivity(Intent(context, FakeSplashAdActivity::class.java))
            }) {
                Text("打开模拟广告")
            }
        }
    }
}

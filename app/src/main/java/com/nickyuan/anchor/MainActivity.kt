package com.nickyuan.anchor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nickyuan.anchor.ui.theme.AnchorTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity：Android 里"一个页面"的单位，类似 Web 里的一个页面。
 * 本项目只有这一个 Activity，以后所有页面都用 Compose 在它里面切换。
 *
 * @AndroidEntryPoint 让 Hilt 可以往这个页面里注入依赖（M0 还没有要注入的东西，先接好）。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // onCreate：页面被创建时系统回调的方法，相当于页面的"入口"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 让内容延伸到状态栏和导航栏下面（Scaffold 会自动留出安全边距）
        enableEdgeToEdge()
        // setContent：用 Compose 描述这个页面要显示什么
        setContent {
            AnchorTheme {
                HomeScreen()
            }
        }
    }
}

/**
 * @Composable 函数：Compose 的界面单元。
 * 它不是"创建控件"，而是"描述界面长什么样"；数据变化时 Compose 会自动重新调用它刷新界面。
 */
@Composable
fun HomeScreen() {
    // Scaffold：Material 页面的标准骨架，负责处理状态栏、导航栏的留白
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Hello, ${stringResource(R.string.app_name)}",
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(12.dp))
            // 占位文字，M2 实现无障碍服务后改为真实状态
            Text(
                text = stringResource(R.string.service_status_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// @Preview：在 Android Studio 右侧直接预览界面，不用装到模拟器上
@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    AnchorTheme {
        HomeScreen()
    }
}

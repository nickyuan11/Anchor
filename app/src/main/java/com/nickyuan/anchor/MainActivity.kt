package com.nickyuan.anchor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nickyuan.anchor.ui.AnchorNavHost
import com.nickyuan.anchor.ui.theme.AnchorTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Activity：Android 里"一个页面"的单位，类似 Web 里的一个页面。
 * 本项目只有这一个 Activity，所有页面都用 Compose 在它里面切换（见 ui/AnchorNavHost.kt）。
 *
 * @AndroidEntryPoint 让 Hilt 可以往这个页面（以及里面的 ViewModel）注入依赖。
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
                AnchorNavHost()
            }
        }
    }
}

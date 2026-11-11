package com.nickyuan.anchor.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nickyuan.anchor.ui.guide.GuideScreen
import com.nickyuan.anchor.ui.home.HomeScreen
import com.nickyuan.anchor.ui.logs.LogsScreen
import com.nickyuan.anchor.ui.settings.SettingsScreen
import com.nickyuan.anchor.ui.snapshots.SnapshotsScreen
import com.nickyuan.anchor.ui.whitelist.WhitelistScreen

/** 页面路由名。每个页面有一个名字，跳转时按名字找，类似 Web 的 URL 路径 */
private object Routes {
    const val HOME = "home"
    const val LOGS = "logs"
    const val SNAPSHOTS = "snapshots"
    const val SETTINGS = "settings"
    const val WHITELIST = "whitelist"
    const val GUIDE = "guide"
}

/**
 * 页面导航：App 只有一个 Activity，不同"页面"其实是在这里切换不同的 Composable。
 * NavHost 负责记录页面栈，按返回键时自动回到上一页。
 */
@Composable
fun AnchorNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenLogs = { navController.navigate(Routes.LOGS) },
                onOpenSnapshots = { navController.navigate(Routes.SNAPSHOTS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenGuide = { navController.navigate(Routes.GUIDE) },
            )
        }
        composable(Routes.LOGS) {
            LogsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SNAPSHOTS) {
            SnapshotsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenWhitelist = { navController.navigate(Routes.WHITELIST) },
            )
        }
        composable(Routes.GUIDE) {
            GuideScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.WHITELIST) {
            WhitelistScreen(onBack = { navController.popBackStack() })
        }
    }
}

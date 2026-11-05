package com.nickyuan.anchor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = AnchorBlue,
    secondary = AnchorTeal,
)

private val DarkColors = darkColorScheme(
    primary = AnchorBlueLight,
    secondary = AnchorTealLight,
)

/**
 * App 统一主题。所有页面都包在 AnchorTheme { ... } 里，就能使用统一的颜色和字体。
 *
 * @param dynamicColor Android 12+ 支持"动态取色"（跟随壁纸颜色），默认开启；
 *                     低版本系统自动使用上面定义的品牌色。
 */
@Composable
fun AnchorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}

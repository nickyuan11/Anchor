package com.nickyuan.anchor.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 调试面板的 release 版：什么都不显示。
 * 与 src/debug/ 下的同名函数签名完全一致，主页代码因此不用区分 debug / release。
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun DebugPanel(modifier: Modifier = Modifier) = Unit

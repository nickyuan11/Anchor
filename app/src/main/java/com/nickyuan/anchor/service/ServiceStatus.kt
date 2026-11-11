package com.nickyuan.anchor.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 跳过服务此刻是否真的在运行。
 *
 * 为什么不能只看系统设置里的开关：国产系统可能在后台把服务杀掉，
 * 这时设置里的开关还是开着的，但服务其实已经不工作了。
 * 无障碍服务和界面在同一个进程里，所以用一个内存里的标记就能知道服务对象是否还连着系统。
 */
@Singleton
class ServiceStatus @Inject constructor() {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun setConnected(connected: Boolean) {
        _connected.value = connected
    }
}

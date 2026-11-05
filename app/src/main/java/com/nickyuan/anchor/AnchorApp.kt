package com.nickyuan.anchor

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application：整个 App 进程的全局单例，进程启动时最先创建，生命周期和进程一样长。
 *
 * @HiltAndroidApp 让 Hilt 在这里建立全局的依赖注入容器，
 * 作用类似 Spring Boot 的 @SpringBootApplication。
 * 需要在 AndroidManifest.xml 的 android:name 里指定这个类才会生效。
 */
@HiltAndroidApp
class AnchorApp : Application()

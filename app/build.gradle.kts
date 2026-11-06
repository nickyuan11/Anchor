// app 模块构建脚本，类似一个子模块的 pom.xml。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    // namespace：代码里 R 类、BuildConfig 类所在的包名
    namespace = "com.nickyuan.anchor"
    // compileSdk：编译时使用的 Android API 版本（只影响编译，不影响能装在哪些手机上）
    compileSdk = 37

    defaultConfig {
        // applicationId：App 在手机上的唯一身份。发出 APK 后不要再改，否则会被当成另一个 App
        applicationId = "com.nickyuan.anchor"
        // minSdk 26 = Android 8.0，低于这个版本的手机装不上
        minSdk = 26
        // targetSdk：声明 App 已适配到哪个 Android 版本，系统据此决定启用哪些新行为
        targetSdk = 37
        versionCode = 4          // 内部版本号，每次发版递增
        versionName = "0.3.1"    // 给人看的版本号
    }

    buildTypes {
        release {
            // 代码混淆/压缩，M5 打正式包时再评估是否开启
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // platform(...) 导入 BOM，类似 Maven 里 <scope>import</scope> 的 BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // 只在 debug 包里包含：Android Studio 预览界面用的工具
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Hilt 依赖注入：运行时库 + 编译期代码生成器（ksp 相当于 Java 的 annotationProcessor）
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    // 在 Compose 页面里用 hiltViewModel() 获取由 Hilt 创建的 ViewModel
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    // Room 数据库：runtime 是运行时库，compiler 在编译期根据注解生成 SQL 代码（类似 MyBatis 的 Mapper 实现）
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore：保存开关等设置
    implementation(libs.androidx.datastore.preferences)
    // kotlinx.serialization：生成快照 JSON（M4 也用它解析规则文件）
    implementation(libs.kotlinx.serialization.json)

    // 单元测试（在电脑上运行，不需要模拟器）
    testImplementation(libs.junit)
}

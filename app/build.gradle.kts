// app 模块构建脚本，类似一个子模块的 pom.xml。
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---------- 正式包签名 ----------
// 签名用的钥匙（keystore）和密码不能进仓库，所以写在项目根目录的 keystore.properties 里
// （已被 .gitignore 排除），格式见 keystore.properties.example，生成步骤见 README.md。
// 没有这个文件时照常构建，只是 release 包不签名（不能安装）。
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProps.getProperty("storeFile") != null

val appVersionName = "1.0.0"

// 生成的 APK 文件名前缀：anchor-1.0.0-release.apk / anchor-1.0.0-debug.apk
base {
    archivesName.set("anchor-$appVersionName")
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
        versionCode = 12          // 内部版本号，每次发版递增；系统靠它判断哪个版本更新
        versionName = appVersionName // 给人看的版本号
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // 代码混淆/压缩（R8）。v1.0 先不开：开启后可能影响 JSON 解析、数据库等靠生成代码工作的部分，
            // 而这类问题只会在真机上暴露。以后想缩小安装包时再开启并充分测试
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
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

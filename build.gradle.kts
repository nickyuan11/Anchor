// 根构建脚本：只声明插件版本（apply false 表示"先登记，不在根项目启用"），
// 真正启用插件的是 app/build.gradle.kts。版本号统一写在 gradle/libs.versions.toml。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

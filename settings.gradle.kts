// settings.gradle.kts：项目级设置，类似 Maven 多模块里的父 pom.xml。
// 这里声明"去哪些仓库下载插件和依赖"，以及项目包含哪些模块。
// 全部使用官方仓库（需要网络能访问 Google / Maven Central，本机通过 VPN 访问）。

pluginManagement {
    repositories {
        google()             // Google Maven：AGP、AndroidX、Compose 等
        mavenCentral()       // Maven Central：Kotlin、Hilt、KSP 等
        gradlePluginPortal() // Gradle 插件门户
    }
}

dependencyResolutionManagement {
    // 只允许在这里统一声明仓库，禁止各模块自己乱加
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Anchor"
include(":app")

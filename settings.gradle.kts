// settings.gradle.kts：项目级设置，类似 Maven 多模块里的父 pom.xml。
// 这里声明"去哪些仓库下载插件和依赖"，以及项目包含哪些模块。

pluginManagement {
    repositories {
        // 阿里云镜像放前面，国内下载更快；镜像里找不到时会自动回落到下面的官方仓库
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 只允许在这里统一声明仓库，禁止各模块自己乱加
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}

rootProject.name = "Anchor"
include(":app")

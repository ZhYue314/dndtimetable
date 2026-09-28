// GitHub Runner 在海外，访问 aliyun 镜像异常会让 Gradle 中断解析（本地/国内才用镜像）
pluginManagement {
    val onCi = System.getenv("GITHUB_ACTIONS") == "true"
    repositories {
        // 优先国内镜像（快），失败回退谷歌官方
        if (!onCi) {
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    val onCi = System.getenv("GITHUB_ACTIONS") == "true"
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 优先国内镜像（快），失败回退官方
        if (!onCi) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "dndtimetable"
include(":app")

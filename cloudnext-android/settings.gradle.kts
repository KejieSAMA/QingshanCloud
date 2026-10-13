@file:Suppress("UnstableApiUsage")

pluginManagement {
    includeBuild("build-logic")
    repositories {
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cloudnext-android"

// M1 阶段已就绪的模块。后续 milestone 会陆续打开下面注释。
include(":app")

include(":core:designsystem")
include(":core:ui")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:common")

include(":data:auth")
include(":data:drive")
include(":data:transfer")
include(":data:preview")

include(":feature:login")
include(":feature:home")
include(":feature:myfiles")
include(":feature:transfer")
include(":feature:preview")

// M5a 模块（回收站 + 搜索 + 共享 + 组织搜索）
include(":data:share")
include(":data:trash")
include(":data:search")
include(":data:org")
include(":feature:shared")
include(":feature:trash")
include(":feature:search")

// M5b 模块（外链 + 工作空间完整 CRUD + 成员）
include(":data:link")
include(":data:workspace")
include(":feature:workspaces")
include(":feature:links")

// M6 模块（设置 + 关于 + 主题 + 缓存清理）
include(":feature:settings")

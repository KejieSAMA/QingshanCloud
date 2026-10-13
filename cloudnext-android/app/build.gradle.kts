plugins {
    alias(libs.plugins.cloudnext.application)
    alias(libs.plugins.cloudnext.compose)
    alias(libs.plugins.cloudnext.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.x.cloudnext.android"

    defaultConfig {
        applicationId = "com.x.cloudnext.android"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 暂时用 debug keystore 签名，让 release APK 可以直接安装到自己的设备做验证。
            // 真正发布前需要：
            //   1) 创建/复用 release keystore（.jks）
            //   2) 在 local.properties 里加 release.keystore.path / .password / .keyAlias / .keyPassword
            //   3) 在 signingConfigs 里加 create("release") {...} 读上面配置
            //   4) 改成 signingConfig = signingConfigs.getByName("release")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
    implementation(project(":core:common"))

    // Data
    implementation(project(":data:auth"))
    implementation(project(":data:drive"))
    implementation(project(":data:transfer"))
    implementation(project(":data:preview"))
    // M5a data 模块
    implementation(project(":data:trash"))
    implementation(project(":data:search"))
    implementation(project(":data:share"))
    implementation(project(":data:org"))
    // M5b data 模块
    implementation(project(":data:link"))
    implementation(project(":data:workspace"))

    // Feature
    implementation(project(":feature:login"))
    implementation(project(":feature:home"))
    implementation(project(":feature:myfiles"))
    implementation(project(":feature:transfer"))
    implementation(project(":feature:preview"))
    // M5a feature 模块
    implementation(project(":feature:trash"))
    implementation(project(":feature:search"))
    implementation(project(":feature:shared"))
    // M5b feature 模块
    implementation(project(":feature:links"))
    implementation(project(":feature:workspaces"))
    // M6 feature 模块
    implementation(project(":feature:settings"))

    // Coil ImageLoader 全局：CoilModule 在 :app 内 provide 注入共享 OkHttp
    implementation(libs.coil.compose)
    implementation(libs.okhttp.core)

    // M6：WindowSizeClass for Tablet 适配
    implementation(libs.compose.material3.windowsize)
    // M6：Baseline Profile installer
    implementation(libs.androidx.profileinstaller)

    // AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.google.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // Hilt + WorkManager
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
}

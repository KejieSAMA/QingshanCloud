import java.util.Properties

plugins {
    alias(libs.plugins.cloudnext.library)
    alias(libs.plugins.cloudnext.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// 从根目录的 local.properties 读 baseUrl（per-user / gitignored）
// providers.gradleProperty 不读 local.properties，所以手动加载。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val debugApiBaseUrl: String =
    localProps.getProperty("cloudnext.api.base.url.debug") ?: "http://10.0.2.2:8088/"
val releaseApiBaseUrl: String =
    localProps.getProperty("cloudnext.api.base.url.release") ?: "https://your-prod-host/"

android {
    namespace = "com.x.cloudnext.android.network"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "API_BASE_URL", "\"$debugApiBaseUrl\"")
    }

    buildTypes {
        release {
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBaseUrl\"")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
}

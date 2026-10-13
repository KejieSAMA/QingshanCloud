plugins {
    alias(libs.plugins.cloudnext.feature)
}

android {
    namespace = "com.x.cloudnext.android.feature.settings"
}

dependencies {
    implementation(project(":core:network"))   // BuildConfig.API_BASE_URL / VERSION_NAME for About
    implementation(project(":core:datastore")) // PreferencesRepository（主题 / wifiOnly）
    implementation(project(":core:database"))  // DriveCacheDao（清缓存）
    implementation(project(":data:transfer"))  // SearchRepository 历史词清除走 PreferencesRepository

    implementation(libs.coil.compose)  // ImageLoader.diskCache 清除
}

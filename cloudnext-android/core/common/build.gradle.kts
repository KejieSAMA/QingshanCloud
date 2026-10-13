plugins {
    alias(libs.plugins.cloudnext.library)
}

android {
    namespace = "com.x.cloudnext.android.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    // 暴露为 api：DateFormatter 的 default 参数类型 Instant/TimeZone 在调用点需要可见
    api(libs.kotlinx.datetime)
    implementation(libs.timber)
}

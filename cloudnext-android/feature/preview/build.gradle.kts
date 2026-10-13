plugins {
    alias(libs.plugins.cloudnext.feature)
}

android {
    namespace = "com.x.cloudnext.android.feature.preview"
}

dependencies {
    implementation(project(":data:preview"))
    implementation(project(":core:network"))
    // OnlyOffice WebView 注入 X-CloudNext-Token 主请求 header
    implementation(project(":core:datastore"))

    // 图片：Coil + Telephoto zoomable
    implementation(libs.coil.compose)
    implementation(libs.zoomable.image.coil)

    // 视频：Media3 ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.okhttp.core)
}

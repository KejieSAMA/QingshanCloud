plugins {
    alias(libs.plugins.cloudnext.library)
    alias(libs.plugins.cloudnext.hilt)
}

android {
    namespace = "com.x.cloudnext.android.data.transfer"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    // M6: 读 wifiOnly 设置决定 NetworkType
    implementation(project(":core:datastore"))
    // 上传完成后调 driveRepository.refreshFolder 自动刷新当前目录
    implementation(project(":data:drive"))
    // M5b: workspace 上传成功后调 workspaceRepository.listItems 刷新
    implementation(project(":data:workspace"))

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.okhttp.core)
    implementation(libs.timber)
}

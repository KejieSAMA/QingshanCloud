plugins {
    alias(libs.plugins.cloudnext.library)
    alias(libs.plugins.cloudnext.hilt)
}

android {
    namespace = "com.x.cloudnext.android.data.trash"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":data:drive"))  // 复用 DriveItem 领域模型 + BatchOperationResult + DriveRepository.refreshFolder

    implementation(libs.kotlinx.coroutines.android)
}

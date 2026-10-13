plugins {
    alias(libs.plugins.cloudnext.feature)
    alias(libs.plugins.kotlin.serialization)  // OrgPicker savedStateHandle 序列化 List<ShareTarget>
}

android {
    namespace = "com.x.cloudnext.android.feature.shared"
}

dependencies {
    implementation(project(":data:drive"))
    implementation(project(":data:share"))
    implementation(project(":data:org"))

    implementation(libs.kotlinx.serialization.json)
}

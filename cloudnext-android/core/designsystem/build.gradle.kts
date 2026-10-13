plugins {
    alias(libs.plugins.cloudnext.library)
    alias(libs.plugins.cloudnext.compose)
}

android {
    namespace = "com.x.cloudnext.android.designsystem"
}

dependencies {
    api(libs.compose.material3)
    api(libs.compose.material3.windowsize)
    api(libs.compose.material.icons.extended)
}

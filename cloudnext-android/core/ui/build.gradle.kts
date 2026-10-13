plugins {
    alias(libs.plugins.cloudnext.library)
    alias(libs.plugins.cloudnext.compose)
}

android {
    namespace = "com.x.cloudnext.android.ui"
}

dependencies {
    api(project(":core:designsystem"))
    api(project(":core:common"))
}

plugins {
    alias(libs.plugins.cloudnext.feature)
}

android {
    namespace = "com.x.cloudnext.android.feature.login"
}

dependencies {
    implementation(project(":core:network"))
    implementation(project(":data:auth"))
}

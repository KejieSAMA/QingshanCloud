plugins {
    alias(libs.plugins.cloudnext.library)
    alias(libs.plugins.cloudnext.hilt)
}

android {
    namespace = "com.x.cloudnext.android.data.auth"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))

    implementation(libs.kotlinx.coroutines.android)
}

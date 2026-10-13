plugins {
    alias(libs.plugins.cloudnext.library)
    alias(libs.plugins.cloudnext.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.x.cloudnext.android.datastore"
}

dependencies {
    implementation(project(":core:common"))

    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}

plugins {
    alias(libs.plugins.cloudnext.feature)
}

android {
    namespace = "com.x.cloudnext.android.feature.links"
}

dependencies {
    implementation(project(":data:link"))
    implementation(project(":core:network"))  // BuildConfig.API_BASE_URL for resolveShareUrl
}

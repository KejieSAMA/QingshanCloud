plugins {
    alias(libs.plugins.cloudnext.feature)
}

android {
    namespace = "com.x.cloudnext.android.feature.search"
}

dependencies {
    implementation(project(":data:drive"))
    implementation(project(":data:search"))
}

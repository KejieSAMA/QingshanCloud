plugins {
    alias(libs.plugins.cloudnext.feature)
}

android {
    namespace = "com.x.cloudnext.android.feature.home"
}

dependencies {
    implementation(project(":data:auth"))
    implementation(project(":data:drive"))
    implementation(project(":core:datastore"))
}

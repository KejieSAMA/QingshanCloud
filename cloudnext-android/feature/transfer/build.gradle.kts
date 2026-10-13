plugins {
    alias(libs.plugins.cloudnext.feature)
}

android {
    namespace = "com.x.cloudnext.android.feature.transfer"
}

dependencies {
    implementation(project(":data:transfer"))
}

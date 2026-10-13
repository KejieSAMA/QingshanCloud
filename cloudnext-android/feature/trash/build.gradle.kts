plugins {
    alias(libs.plugins.cloudnext.feature)
}

android {
    namespace = "com.x.cloudnext.android.feature.trash"
}

dependencies {
    implementation(project(":data:drive"))
    implementation(project(":data:trash"))
}

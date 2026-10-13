plugins {
    alias(libs.plugins.cloudnext.feature)
}

android {
    namespace = "com.x.cloudnext.android.feature.workspaces"
}

dependencies {
    implementation(project(":data:drive"))
    implementation(project(":data:workspace"))
    implementation(project(":data:transfer"))
}

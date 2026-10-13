plugins {
    alias(libs.plugins.cloudnext.feature)
}

android {
    namespace = "com.x.cloudnext.android.feature.myfiles"
}

dependencies {
    implementation(project(":data:drive"))
    implementation(project(":data:transfer"))
    implementation(project(":core:network"))
    implementation(project(":core:datastore"))
}

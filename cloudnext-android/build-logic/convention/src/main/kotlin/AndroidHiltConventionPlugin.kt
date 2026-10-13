import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Hilt 卷积配置。任何使用 @AndroidEntryPoint / @HiltViewModel / @HiltAndroidApp 的 module
 * 都需要 apply 这个插件。同时引入 KSP（Hilt 2.48 起官方支持 KSP，比 KAPT 快很多）。
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.google.devtools.ksp")
                apply("com.google.dagger.hilt.android")
            }
            dependencies {
                add("implementation", libs.findLibrary("hilt-android").get())
                add("ksp", libs.findLibrary("hilt-compiler").get())
            }
        }
    }
}

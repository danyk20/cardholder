import io.github.danyk20.cardholder.buildlogic.libs
import io.github.danyk20.cardholder.buildlogic.library
import io.github.danyk20.cardholder.buildlogic.plugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** A feature module: Compose UI + Hilt ViewModels + type-safe navigation. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("cardholder.android.library.compose")
            pluginManager.apply("cardholder.hilt")
            pluginManager.apply(libs.plugin("kotlin-serialization"))

            dependencies {
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:domain"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:ui"))

                add("implementation", libs.library("androidx-hilt-lifecycle-viewModelCompose"))
                add("implementation", libs.library("androidx-lifecycle-runtimeCompose"))
                add("implementation", libs.library("androidx-lifecycle-viewModelCompose"))
                add("implementation", libs.library("androidx-navigation-compose"))
                add("implementation", libs.library("kotlinx-serialization-json"))

                add("testImplementation", project(":core:testing"))
                add("testImplementation", libs.library("robolectric"))
                add("testImplementation", libs.library("androidx-test-ext-junit"))
                add("testImplementation", libs.library("androidx-compose-ui-test-junit4"))
                add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
            }
        }
    }
}

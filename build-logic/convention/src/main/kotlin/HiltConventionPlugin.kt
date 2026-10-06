import io.github.danyk20.cardholder.buildlogic.libs
import io.github.danyk20.cardholder.buildlogic.library
import io.github.danyk20.cardholder.buildlogic.plugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("ksp"))
            dependencies {
                add("ksp", libs.library("hilt-compiler"))
            }

            // Pure JVM modules only need the Dagger/Hilt annotations.
            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                dependencies {
                    add("implementation", libs.library("hilt-core"))
                }
            }

            // Android modules get the Hilt Gradle plugin and runtime.
            pluginManager.withPlugin("com.android.base") {
                pluginManager.apply(libs.plugin("hilt"))
                dependencies {
                    add("implementation", libs.library("hilt-android"))
                }
            }
        }
    }
}

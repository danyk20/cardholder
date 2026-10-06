import io.github.danyk20.cardholder.buildlogic.configureKotlinJvm
import io.github.danyk20.cardholder.buildlogic.configureStaticAnalysis
import io.github.danyk20.cardholder.buildlogic.libs
import io.github.danyk20.cardholder.buildlogic.plugin
import org.gradle.api.Plugin
import org.gradle.api.Project

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("kotlin-jvm"))
            pluginManager.apply(libs.plugin("android-lint"))
            configureKotlinJvm()
            configureStaticAnalysis()
        }
    }
}

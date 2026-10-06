import com.android.build.api.dsl.ApplicationExtension
import io.github.danyk20.cardholder.buildlogic.configureKotlinAndroid
import io.github.danyk20.cardholder.buildlogic.configureStaticAnalysis
import io.github.danyk20.cardholder.buildlogic.libs
import io.github.danyk20.cardholder.buildlogic.plugin
import io.github.danyk20.cardholder.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("android-application"))
            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = libs.version("targetSdk").toInt()
                testOptions.animationsDisabled = true
                lint {
                    warningsAsErrors = true
                    abortOnError = true
                    checkDependencies = true
                    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "OldTargetApi")
                }
            }
            configureStaticAnalysis()
        }
    }
}

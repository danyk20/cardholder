import com.android.build.api.dsl.LibraryExtension
import io.github.danyk20.cardholder.buildlogic.configureKotlinAndroid
import io.github.danyk20.cardholder.buildlogic.configureStaticAnalysis
import io.github.danyk20.cardholder.buildlogic.libs
import io.github.danyk20.cardholder.buildlogic.plugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("android-library"))
            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                defaultConfig.consumerProguardFiles("consumer-rules.pro")
                testOptions.animationsDisabled = true
                testOptions.unitTests.isIncludeAndroidResources = true
                lint {
                    warningsAsErrors = true
                    abortOnError = true
                    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
                }
            }
            configureStaticAnalysis()
        }
    }
}

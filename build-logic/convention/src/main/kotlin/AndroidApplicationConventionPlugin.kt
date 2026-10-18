import com.android.build.api.dsl.ApplicationExtension
import io.github.danyk20.cardholder.buildlogic.configureKotlinAndroid
import io.github.danyk20.cardholder.buildlogic.configureLintDefaults
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
                    configureLintDefaults()
                    // The app's lint run also covers every module it depends on.
                    checkDependencies = true
                    disable += "OldTargetApi"
                }
            }
            configureStaticAnalysis()
        }
    }
}

import com.android.build.api.dsl.ApplicationExtension
import io.github.danyk20.cardholder.buildlogic.configureAndroidCompose
import io.github.danyk20.cardholder.buildlogic.libs
import io.github.danyk20.cardholder.buildlogic.plugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidApplicationComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("cardholder-android-application"))
            configureAndroidCompose(extensions.getByType<ApplicationExtension>())
        }
    }
}

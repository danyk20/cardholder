import com.android.build.api.dsl.LibraryExtension
import io.github.danyk20.cardholder.buildlogic.configureAndroidCompose
import io.github.danyk20.cardholder.buildlogic.libs
import io.github.danyk20.cardholder.buildlogic.plugin
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.getByType

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("cardholder-android-library"))
            configureAndroidCompose(extensions.getByType<LibraryExtension>())
        }
    }
}

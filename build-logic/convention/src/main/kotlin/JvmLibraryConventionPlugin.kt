import com.android.build.api.dsl.Lint
import io.github.danyk20.cardholder.buildlogic.configureKotlinJvm
import io.github.danyk20.cardholder.buildlogic.configureLintDefaults
import io.github.danyk20.cardholder.buildlogic.configureStaticAnalysis
import io.github.danyk20.cardholder.buildlogic.libs
import io.github.danyk20.cardholder.buildlogic.plugin
import io.github.danyk20.cardholder.buildlogic.registerUnitTests
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("kotlin-jvm"))
            pluginManager.apply(libs.plugin("android-lint"))
            extensions.configure<Lint> { configureLintDefaults() }
            configureKotlinJvm()
            configureStaticAnalysis()
            registerUnitTests("test")
        }
    }
}

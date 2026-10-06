package io.github.danyk20.cardholder.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

private val KTLINT_RULES = mapOf(
    "ktlint_code_style" to "android_studio",
    "max_line_length" to "120",
    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
)

/** Applies Spotless (ktlint) and detekt with the shared project configuration. */
internal fun Project.configureStaticAnalysis() {
    pluginManager.apply(libs.plugin("spotless"))
    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(libs.version("ktlint"))
                .editorConfigOverride(KTLINT_RULES)
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.version("ktlint"))
                .editorConfigOverride(KTLINT_RULES)
        }
    }

    pluginManager.apply(libs.plugin("kover"))

    pluginManager.apply(libs.plugin("detekt"))
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel = true
    }
}

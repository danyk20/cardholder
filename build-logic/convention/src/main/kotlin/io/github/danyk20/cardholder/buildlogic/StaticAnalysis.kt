package io.github.danyk20.cardholder.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure

private val KTLINT_RULES = mapOf(
    "ktlint_code_style" to "android_studio",
    "max_line_length" to "120",
    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
)

/** The Kover variant of the coverage report: `./gradlew koverHtmlReportUnit`. */
private const val COVERAGE_VARIANT = "unit"

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
    extensions.configure<KoverProjectExtension> {
        currentProject {
            // Coverage of what the unit tests run on. Kover's default (total) variant would also compile the
            // release build of every module just for the report.
            createVariant(COVERAGE_VARIANT) { add("debug", "githubDebug", "jvm", optional = true) }
            // Kover's agent slows the tests down and changes their build cache key, so tests only run with it
            // when a coverage report was asked for.
            instrumentation.disabledForAll = gradle.startParameter.taskNames.none { "kover" in it.lowercase() }
        }
    }

    pluginManager.apply(libs.plugin("detekt"))
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel = true
    }
}

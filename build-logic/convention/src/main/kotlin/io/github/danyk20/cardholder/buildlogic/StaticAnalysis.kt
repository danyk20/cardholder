package io.github.danyk20.cardholder.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Applies Spotless (ktlint) and detekt with the shared project configuration. */
internal fun Project.configureStaticAnalysis() {
    pluginManager.apply(libs.plugin("spotless"))
    extensions.configure<SpotlessExtension> {
        kotlin {
            target("src/**/*.kt")
            ktlint(libs.version("ktlint"))
                .setEditorConfigPath(rootProject.file(".editorconfig"))
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.version("ktlint"))
                .setEditorConfigPath(rootProject.file(".editorconfig"))
        }
    }

    pluginManager.apply(libs.plugin("detekt"))
    extensions.configure<DetektExtension> {
        buildUponDefaultConfig = true
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel = true
    }
}

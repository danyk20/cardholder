package io.github.danyk20.cardholder.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

private val JAVA_VERSION = JavaVersion.VERSION_17
private val JVM_TARGET = JvmTarget.JVM_17

/** Shared Android configuration for application and library modules. */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        compileOptions.sourceCompatibility = JAVA_VERSION
        compileOptions.targetCompatibility = JAVA_VERSION
    }
    configureKotlin<KotlinAndroidProjectExtension>()
    configureTests()
}

/** Configuration for pure Kotlin/JVM modules. */
internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JAVA_VERSION
        targetCompatibility = JAVA_VERSION
    }
    configureKotlin<KotlinJvmProjectExtension>()
    configureTests()
}

private inline fun <reified T> Project.configureKotlin()
    where T : HasConfigurableKotlinCompilerOptions<KotlinJvmCompilerOptions>, T : Any =
    extensions.configure<T> {
        compilerOptions {
            jvmTarget = JVM_TARGET
            allWarningsAsErrors = providers.gradleProperty("warningsAsErrors").map(String::toBoolean).orElse(false)
            freeCompilerArgs.addAll(
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-Xconsistent-data-class-copy-visibility",
            )
        }
    }

private fun Project.configureTests() {
    tasks.withType<Test>().configureEach {
        // Not every module has tests (yet); an empty test task must not fail the build.
        failOnNoDiscoveredTests = false
    }
    dependencies {
        add("testImplementation", libs.library("junit"))
        add("testImplementation", libs.library("kotlin-test"))
        add("testImplementation", libs.library("kotlinx-coroutines-test"))
        add("testImplementation", libs.library("turbine"))
    }
}

/** Lint settings shared by the app and library modules. */
internal fun Lint.configureLintDefaults() {
    warningsAsErrors = true
    abortOnError = true
    // Tests aren't shipped; analysing them doubles lint's work for no user-facing benefit.
    ignoreTestSources = true
    // Dependency updates are Dependabot's job, not a reason to fail the build.
    disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
}

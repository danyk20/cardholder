plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless) apply false
}

dependencies {
    // Aggregated coverage report of every module with tests: ./gradlew koverHtmlReportUnit
    listOf(
        projects.app,
        projects.core.barcode,
        projects.core.data,
        projects.core.database,
        projects.core.domain,
        projects.core.location,
        projects.core.model,
        projects.core.scanning,
        projects.core.security,
        projects.core.storage,
        projects.core.ui,
        projects.feature.barcodefullscreen,
        projects.feature.carddetail,
        projects.feature.cardeditor,
        projects.feature.cardlist,
        projects.feature.settings,
    ).forEach { kover(it) }
}

kover {
    currentProject {
        // The modules' "unit" variants: debug builds only (see StaticAnalysis.kt).
        createVariant("unit") {}
    }
    reports {
        filters {
            excludes {
                annotatedBy("androidx.compose.ui.tooling.preview.Preview")
                classes("*_Factory", "*_HiltModules*", "*Hilt_*", "*_Impl*", "*ComposableSingletons*", "*.BuildConfig")
                packages("hilt_aggregated_deps", "dagger.hilt.internal.aggregatedroot.codegen")
            }
        }
    }
}

// CI runs the unit tests in two halves on separate machines. Modules join their half by their path.
tasks.register("unitTestsCore") {
    group = "verification"
    description = "Runs the unit tests of the core modules."
    dependsOn(subprojects.filter { it.path.startsWith(":core:") }.map { "${it.path}:unitTests" })
}

tasks.register("unitTestsFeatures") {
    group = "verification"
    description = "Runs the unit tests of the feature modules and the app."
    dependsOn(
        subprojects.filter { it.path.startsWith(":feature:") || it.path == ":app" }.map { "${it.path}:unitTests" },
    )
}

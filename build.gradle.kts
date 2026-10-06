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
    // Aggregated coverage report: ./gradlew koverHtmlReport
    listOf(
        projects.core.data,
        projects.core.database,
        projects.core.domain,
        projects.core.model,
        projects.core.security,
        projects.core.storage,
        projects.feature.barcodefullscreen,
        projects.feature.carddetail,
        projects.feature.cardeditor,
        projects.feature.cardlist,
        projects.feature.settings,
    ).forEach { kover(it) }
}

kover {
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

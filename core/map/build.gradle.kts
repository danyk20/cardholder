plugins {
    alias(libs.plugins.cardholder.android.library.compose)
}

android {
    namespace = "io.github.danyk20.cardholder.core.map"
}

dependencies {
    // The public API uses the domain's GeoPoint, so the map can't be used without the domain.
    api(projects.core.domain)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mapcompose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

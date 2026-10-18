plugins {
    alias(libs.plugins.cardholder.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.danyk20.cardholder.feature.barcodefullscreen"
}

dependencies {
    implementation(projects.core.barcode)

    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}

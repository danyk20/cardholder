plugins {
    alias(libs.plugins.cardholder.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.danyk20.cardholder.feature.cardlist"
}

dependencies {
    implementation(projects.core.barcode)
    implementation(libs.reorderable)

    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}

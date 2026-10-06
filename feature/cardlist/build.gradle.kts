plugins {
    alias(libs.plugins.cardholder.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.danyk20.cardholder.feature.cardlist"
}

dependencies {
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    implementation(projects.core.barcode)
    implementation(libs.reorderable)
}

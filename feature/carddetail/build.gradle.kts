plugins {
    alias(libs.plugins.cardholder.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.danyk20.cardholder.feature.carddetail"
}

dependencies {
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
}

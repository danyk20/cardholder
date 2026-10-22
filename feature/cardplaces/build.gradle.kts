plugins {
    alias(libs.plugins.cardholder.android.feature)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.danyk20.cardholder.feature.cardplaces"
}

dependencies {
    implementation(projects.core.map)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.roborazzi)
    testImplementation(libs.turbine)
    testImplementation(libs.roborazzi.compose)
}

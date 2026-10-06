plugins {
    alias(libs.plugins.cardholder.android.feature)
}

android {
    namespace = "io.github.danyk20.cardholder.feature.cardeditor"
}

dependencies {
    implementation(projects.core.scanning)
    implementation(libs.coil.compose)
}

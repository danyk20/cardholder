plugins {
    alias(libs.plugins.cardholder.android.feature)
}

android {
    namespace = "io.github.danyk20.cardholder.feature.cardlist"
}

dependencies {
    implementation(projects.core.barcode)
}

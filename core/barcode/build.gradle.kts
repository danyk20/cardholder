plugins {
    alias(libs.plugins.cardholder.android.library.compose)
}

android {
    namespace = "io.github.danyk20.cardholder.core.barcode"
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.zxing.core)
}

plugins {
    alias(libs.plugins.cardholder.android.library.compose)
}

android {
    namespace = "io.github.danyk20.cardholder.core.designsystem"
}

dependencies {
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.iconsExtended)
    api(libs.androidx.compose.ui)
}

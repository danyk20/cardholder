plugins {
    alias(libs.plugins.cardholder.android.library.compose)
}

android {
    namespace = "io.github.danyk20.cardholder.core.ui"
}

dependencies {
    api(projects.core.designsystem)
    api(projects.core.model)
    api(projects.core.domain)
    implementation(libs.androidx.activity.compose)
    api(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.coil.compose)
    implementation(libs.javax.inject)
}

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
    api(libs.androidx.fragment.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.coil.compose)
    implementation(libs.javax.inject)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.robolectric)
}

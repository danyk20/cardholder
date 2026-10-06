plugins {
    alias(libs.plugins.cardholder.android.library)
    alias(libs.plugins.cardholder.hilt)
}

android {
    namespace = "io.github.danyk20.cardholder.core.storage"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.security)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
}

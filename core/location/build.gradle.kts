plugins {
    alias(libs.plugins.cardholder.android.library)
    alias(libs.plugins.cardholder.hilt)
}

android {
    namespace = "io.github.danyk20.cardholder.core.location"
}

dependencies {
    api(projects.core.domain)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
}

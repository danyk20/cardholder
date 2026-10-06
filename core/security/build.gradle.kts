plugins {
    alias(libs.plugins.cardholder.android.library)
    alias(libs.plugins.cardholder.hilt)
}

android {
    namespace = "io.github.danyk20.cardholder.core.security"
    testFixtures.enable = true
}

dependencies {
    api(projects.core.domain)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.kotlinx.coroutines.android)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test)
}

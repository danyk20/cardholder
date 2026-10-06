plugins {
    alias(libs.plugins.cardholder.android.library)
    alias(libs.plugins.cardholder.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.danyk20.cardholder.core.data"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.database)
    implementation(projects.core.security)
    implementation(projects.core.storage)
    implementation(libs.androidx.dataStore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
}

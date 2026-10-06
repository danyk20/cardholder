plugins {
    alias(libs.plugins.cardholder.android.library)
    alias(libs.plugins.cardholder.android.room)
    alias(libs.plugins.cardholder.hilt)
}

android {
    namespace = "io.github.danyk20.cardholder.core.database"
}

dependencies {
    implementation(projects.core.security)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

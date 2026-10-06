plugins {
    alias(libs.plugins.cardholder.android.library)
    alias(libs.plugins.cardholder.android.room)
    alias(libs.plugins.cardholder.hilt)
}

android {
    namespace = "io.github.danyk20.cardholder.core.database"
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    // Exported schemas are needed by MigrationTestHelper.
    sourceSets.getByName("test").assets.directories.add("$projectDir/schemas")
}

dependencies {
    implementation(projects.core.security)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}

plugins {
    alias(libs.plugins.cardholder.android.library.compose)
    alias(libs.plugins.cardholder.hilt)
}

android {
    namespace = "io.github.danyk20.cardholder.core.scanning"
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.document.scanner)
}

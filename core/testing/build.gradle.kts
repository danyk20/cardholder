plugins {
    alias(libs.plugins.cardholder.android.library)
}

android {
    namespace = "io.github.danyk20.cardholder.core.testing"
}

dependencies {
    api(projects.core.domain)
    api(libs.junit)
    api(libs.kotlin.test)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}

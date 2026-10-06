plugins {
    alias(libs.plugins.cardholder.android.application.compose)
    alias(libs.plugins.cardholder.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.danyk20.cardholder"

    defaultConfig {
        applicationId = "io.github.danyk20.cardholder"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            // Provided by CI (see .github/workflows/release.yml); release builds are unsigned locally otherwise.
            providers.environmentVariable("SIGNING_STORE_FILE").orNull?.let { path ->
                storeFile = file(path)
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.security)
    implementation(projects.core.storage)
    implementation(projects.core.ui)

    implementation(projects.feature.barcodefullscreen)
    implementation(projects.feature.carddetail)
    implementation(projects.feature.cardeditor)
    implementation(projects.feature.cardlist)
    implementation(projects.feature.settings)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.hilt.lifecycle.viewModelCompose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(projects.core.testing)
}

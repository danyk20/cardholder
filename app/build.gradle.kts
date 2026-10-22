plugins {
    alias(libs.plugins.cardholder.android.application.compose)
    alias(libs.plugins.cardholder.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.danyk20.cardholder"

    defaultConfig {
        applicationId = "io.github.danyk20.cardholder"
        versionCode = 11
        versionName = "1.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        // Lets users pick the app language in system settings (Android 13+).
        generateLocaleConfig = true
    }

    // Where the app is distributed decides how it updates itself. Google Play doesn't allow apps from Play to
    // install updates any other way, so the GitHub updater (and its install permission) is only in "github".
    flavorDimensions += "distribution"
    productFlavors {
        create("play") {
            dimension = "distribution"
        }
        create("github") {
            dimension = "distribution"
        }
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

    testOptions.unitTests.isIncludeAndroidResources = true
    // Robolectric's SDK pin (see config/robolectric), as in the library modules.
    sourceSets.getByName("test").resources.directories.add(
        rootProject.layout.projectDirectory.dir("config/robolectric").asFile.path,
    )

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.domain)
    implementation(projects.core.location)
    implementation(projects.core.security)
    implementation(projects.core.storage)
    implementation(projects.core.ui)

    implementation(projects.feature.barcodefullscreen)
    implementation(projects.feature.carddetail)
    implementation(projects.feature.cardeditor)
    implementation(projects.feature.cardlist)
    implementation(projects.feature.cardplaces)
    implementation(projects.feature.settings)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.hilt.lifecycle.viewModelCompose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)

    "playImplementation"(libs.play.appUpdate.ktx)

    testImplementation(projects.core.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}

// The flavors replace the app's testDebugUnitTest and lintDebug with one task per flavor. Keep the usual
// names (used by CI and in the docs) so they still cover the whole app.
tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Runs the unit tests of both distribution flavors."
    dependsOn("testPlayDebugUnitTest", "testGithubDebugUnitTest")
}
tasks.register("lintDebug") {
    group = "verification"
    description = "Runs lint on both distribution flavors."
    dependsOn("lintPlayDebug", "lintGithubDebug")
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.caseymisc.fitdash"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.caseymisc.fitdash"
        minSdk = 28
        targetSdk = 35
        // Increases with every CI run so each new APK installs as an update.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.0"
    }

    // One fixed key for every build. CI machines otherwise generate a new debug key each
    // run, and Android refuses to update an app signed with a different key ("App not
    // installed"). CI writes the key from the FITDASH_KEYSTORE_B64 secret to
    // signing/fitdash-sideload.jks; without it, builds fall back to the default debug key.
    val sideloadKeystore = rootProject.file("signing/fitdash-sideload.jks")
    signingConfigs {
        create("sideload") {
            storeFile = sideloadKeystore
            storePassword = System.getenv("FITDASH_KEYSTORE_PASSWORD") ?: "fitdash-sideload"
            keyAlias = "fitdash"
            keyPassword = System.getenv("FITDASH_KEYSTORE_PASSWORD") ?: "fitdash-sideload"
        }
    }
    val apkSigning = signingConfigs.getByName(if (sideloadKeystore.exists()) "sideload" else "debug")

    buildTypes {
        debug {
            signingConfig = apkSigning
        }
        release {
            isMinifyEnabled = false
            signingConfig = apkSigning
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.health.connect)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}

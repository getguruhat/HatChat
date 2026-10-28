plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.getguruhat.hatchat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.getguruhat.hatchat"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.1.1"
    }

    // CI passes a permanent key (from GitHub secrets) so new builds install over old ones.
    // Without it, builds fall back to the throwaway debug key.
    val keystoreFile = System.getenv("HATCHAT_KEYSTORE_FILE")?.let(::file)?.takeIf { it.exists() }
    val stableSigning = keystoreFile?.let {
        signingConfigs.create("hatchat") {
            storeFile = it
            storeType = "pkcs12"
            storePassword = System.getenv("HATCHAT_KEYSTORE_PASSWORD")
            keyAlias = "hatchat"
            keyPassword = System.getenv("HATCHAT_KEYSTORE_PASSWORD")
        }
    }

    buildTypes {
        debug {
            stableSigning?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = false
            signingConfig = stableSigning ?: signingConfigs.getByName("debug")
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
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release builds pass -PappVersion=0.1.2 from the git tag; versionCode is derived so it
// always increases (0.1.2 -> 102). Same scheme as Kissa.
val appVersion = (findProperty("appVersion") as String?) ?: "0.1.1"
val appVersionCode = appVersion.split(".").map { it.toInt() }.let { parts ->
    parts.getOrElse(0) { 0 } * 10000 + parts.getOrElse(1) { 0 } * 100 + parts.getOrElse(2) { 0 }
}

android {
    namespace = "com.getguruhat.hatchat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.getguruhat.hatchat"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
        // GitHub repo ("owner/name") whose releases the app checks for updates.
        buildConfigField("String", "UPDATE_REPO", "\"getguruhat/HatChat\"")
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}

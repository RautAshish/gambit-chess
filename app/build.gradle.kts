plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    // Apply only if you wire up multiplayer; needs google-services.json.
    // id("com.google.gms.google-services")
}

android {
    buildFeatures {
        buildConfig = true
    }
    signingConfigs {
        create("release") {
            // Provided by CI secrets (see RELEASE.md). Absent locally/on forks,
            // the release build falls back to debug signing so it stays runnable.
            val ks = System.getenv("EMERSION_KEYSTORE_PATH")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("EMERSION_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("EMERSION_KEY_ALIAS")
                keyPassword = System.getenv("EMERSION_KEY_PASSWORD")
            }
        }
    }
    packaging {
        jniLibs {
            // Stockfish ships as jniLibs/<abi>/libstockfish.so and must be
            // EXTRACTED to nativeLibraryDir to be executable (W^X on API 29+).
            useLegacyPackaging = true
        }
    }

    namespace = "com.chessapp"
    compileSdk = 35

    defaultConfig {
        // Permanent Play Store identity (namespace above stays com.chessapp:
        // internal code packages, invisible outside the build).
        applicationId = "io.github.emersionplay.chess"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"

        // Optional baked-in online server so store users need ZERO setup: the
        // developer sets two CI secrets and every build ships pre-configured.
        // Blank values keep the in-app setup flow (self-hosters, forks).
        buildConfigField("String", "DEFAULT_ONLINE_PROJECT_ID",
            "\"${System.getenv("EMERSION_ONLINE_PROJECT_ID") ?: ""}\"")
        buildConfigField("String", "DEFAULT_ONLINE_API_KEY",
            "\"${System.getenv("EMERSION_ONLINE_API_KEY") ?: ""}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val ks = System.getenv("EMERSION_KEYSTORE_PATH")
            signingConfig = if (ks != null && file(ks).exists())
                signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Persistence
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Multiplayer (optional — requires google-services.json + uncommenting the plugin)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

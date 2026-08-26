plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

val productionRelease = providers.gradleProperty("productionRelease").orElse("false").get().toBoolean()

android {
    namespace = "com.vokie"
    val configuredVersionName = providers.gradleProperty("versionName").orElse("1.0.0")
    val configuredVersionCode = providers.gradleProperty("versionCode").orElse("1")
    val keystorePath = providers.environmentVariable("ANDROID_KEYSTORE_PATH")
    val keystorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD")
    val keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS")
    val keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD")
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vokie"
        minSdk = 24
        targetSdk = 34
        versionCode = configuredVersionCode.get().toIntOrNull() ?: error("versionCode must be an integer")
        versionName = configuredVersionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        // Production targets 64-bit ARM devices; this enables the validated dot-product/FP16 path.
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake { arguments += listOf("-DANDROID_STL=c++_shared") }
        }
    }

    signingConfigs {
        if (keystorePath.isPresent && keystorePassword.isPresent && keyAlias.isPresent && keyPassword.isPresent) {
            create("release") {
                storeFile = file(keystorePath.get())
                storePassword = keystorePassword.get()
                this.keyAlias = keyAlias.get()
                this.keyPassword = keyPassword.get()
            }
        }
    }

    buildTypes {
        release {
            val signingConfigured = keystorePath.isPresent && keystorePassword.isPresent && keyAlias.isPresent && keyPassword.isPresent
            if (productionRelease && !signingConfigured) {
                throw GradleException("Production release signing is not configured. Set ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS, and ANDROID_KEY_PASSWORD.")
            }
            if (signingConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    ndkVersion = "27.0.12077973"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    androidResources {
        // Native engines need filesystem copies; keep APK model entries stored to avoid recompression.
        noCompress += listOf("bin", "onnx", "txt", "json")
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// Release assets are staged by scripts/stage-bundled-models.py from a protected archive.
// Never add model binaries to Git; a production APK without all assets is invalid.
tasks.configureEach {
    if (name == "assembleRelease" || name == "bundleRelease") {
        doFirst {
            if (productionRelease && !file("src/main/assets/models/manifest.json").isFile) {
                throw GradleException("Bundled model assets are missing. Run scripts/stage-bundled-models.py in the protected release environment.")
            }
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    // Core
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Official sherpa-onnx Android AAR, pinned to the upstream GitHub release.
    implementation("com.k2fsa:sherpa-onnx:1.13.6@aar")

    // DataStore for settings
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

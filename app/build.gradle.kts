plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.novelreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.novelreader"
        minSdk = 26
        targetSdk = 35

        // VersionCode auto-incrémenté via GitHub Actions ou nombre de commits Git
        val runNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
        val commitCount = try {
            Runtime.getRuntime().exec("git rev-list --count HEAD").inputStream.bufferedReader().readLine()?.toIntOrNull()
        } catch (_: Exception) { null }

        versionCode = runNumber ?: commitCount ?: 1
        versionName = "1.0.${versionCode}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val keystoreFile = rootProject.file("novelreader.keystore")
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "novelreader"
                keyAlias = System.getenv("KEY_ALIAS") ?: "novelreader"
                keyPassword = System.getenv("KEY_PASSWORD") ?: "novelreader"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Utilise le keystore release s'il existe, sinon le debug signing par défaut
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.findByName("debug")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    @Suppress("DEPRECATION")
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    // Coil
    implementation(libs.coil.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // DataStore
    implementation(libs.datastore.preferences)
    implementation(libs.documentfile)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.hilt)
    ksp(libs.hilt.work.compiler)

    // JSON
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}

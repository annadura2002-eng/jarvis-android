plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jarvis.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jarvis.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "7.0"
        
        // Берем ключ из GitHub Secrets (или ставим пустоту, если его нет)
        val apiKey = System.getenv("PROXY_API_KEY") ?: ""
        buildConfigField("String", "DEFAULT_API_KEY", "\"$apiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: "jarvis123"
            keyAlias = System.getenv("KEY_ALIAS") ?: "jarvis"
            keyPassword = System.getenv("KEY_PASSWORD") ?: "jarvis123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.alphacephei:vosk-android:0.3.47@aar")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

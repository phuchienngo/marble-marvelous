plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.phuchienngo.marblemarvelous"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.phuchienngo.marblemarvelous"
        minSdk = 36
        targetSdk = 36
        versionCode = 28
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
        compose = true
    }

    lint {
        abortOnError = false
    }

}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    val filamentVersion = "1.72.0"

    // --- Jetpack Compose (small Android UI surfaces such as runtime-permission screens) ---
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")

    // --- Filament renderer for direct live-wallpaper rendering. ---
    implementation("com.google.android.filament:filament-android:$filamentVersion")
    implementation("com.google.android.filament:filamat-android:$filamentVersion")

    // --- Kotlin coroutines (background weather fetches) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // --- OkHttp + official coroutines adapter (OpenWeather cloud-tile downloads) ---
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:okhttp-coroutines:5.4.0")

    testImplementation("junit:junit:4.13.2")
}

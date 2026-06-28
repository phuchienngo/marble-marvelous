plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.phuchienngo.marblemarvelous"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.phuchienngo.marblemarvelous"
        minSdk = 24
        targetSdk = 35
        versionCode = 28
        versionName = "9 PP-8.0+ arm64"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    // --- Jetpack Compose (small Android UI surfaces such as runtime-permission screens) ---
    implementation(platform("androidx.compose:compose-bom:2026.06.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")

    // --- libGDX 1.14.2 (native libgdx.so from gdx-platform natives in jniLibs) ---
    implementation("com.badlogicgames.gdx:gdx:1.14.2")
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.14.2")

    // --- Kotlin coroutines (background weather fetches) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // --- Dagger 2 DI with @Inject constructor injection via KSP (AGP 8 + KGP). ---
    implementation("com.google.dagger:dagger:2.60")
    ksp("com.google.dagger:dagger-compiler:2.60")

    // --- OkHttp + official coroutines adapter (OpenWeather cloud-tile downloads) ---
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("com.squareup.okhttp3:okhttp-coroutines:5.4.0")

    testImplementation("junit:junit:4.13.2")
}

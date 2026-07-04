import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.isFile) {
    localPropertiesFile.inputStream().use { inputStream ->
        localProperties.load(inputStream)
    }
}
val localOpenWeatherApiKey = localProperties.getProperty("OPENWEATHER_API_KEY", "")
val openWeatherApiKeyProvider = providers
    .gradleProperty("OPENWEATHER_API_KEY")
    .orElse(providers.environmentVariable("OPENWEATHER_API_KEY"))
    .orElse(localOpenWeatherApiKey)

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
        resValue("string", "openweather_api_key", openWeatherApiKeyProvider.get())

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
    val daggerVersion = "2.60"
    val filamentVersion = "1.72.0"
    val okhttpVersion = "5.4.0"

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

    // --- Dagger dependency graph for wallpaper runtime dependencies ---
    implementation("com.google.dagger:dagger:$daggerVersion")
    ksp("com.google.dagger:dagger-compiler:$daggerVersion")

    // --- OkHttp for OpenWeather cloud-tile downloads ---
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")

    testImplementation("junit:junit:4.13.2")
}

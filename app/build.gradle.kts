plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.phuchienngo.marblemarvelous"
    compileSdk = 35

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
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = false
    }

    lint {
        abortOnError = false
    }

    // Generated protobuf sources (regenerated from src/main/proto via protoc) live in a
    // separate root so they're not mixed with hand-written code in src/main/java.
    sourceSets {
        getByName("main") {
            java.srcDir("src/main/generated/java")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

configurations.all {
    resolutionStrategy {
        // androidx.core 1.16+ requires compileSdk 36 (not installed); pin to a 35-compatible version.
        force("androidx.core:core:1.15.0")
    }
}

dependencies {
    // --- libGDX 1.14.2 (native libgdx.so from gdx-platform natives in jniLibs) ---
    implementation("com.badlogicgames.gdx:gdx:1.14.2")
    implementation("com.badlogicgames.gdx:gdx-backend-android:1.14.2")

    // --- Protobuf javalite runtime (StormProtos regenerated from
    // app/src/main/proto/storm_locations.proto via protoc 25.5 --java_out=lite:) ---
    implementation("com.google.protobuf:protobuf-javalite:3.25.5")

    // --- Kotlin coroutines (background weather fetches) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // --- Dagger 2 DI with @Inject constructor injection via KSP (AGP 8 + KGP). ---
    implementation("com.google.dagger:dagger:2.60")
    ksp("com.google.dagger:dagger-compiler:2.60")

    // --- OkHttp + official coroutines adapter (OpenWeather cloud-tile downloads) ---
    // Pinned to 5.3.2: okhttp-android 5.4.0 requires compileSdk 36 (only 35 installed).
    implementation("com.squareup.okhttp3:okhttp:5.3.2")
    implementation("com.squareup.okhttp3:okhttp-coroutines:5.3.2")

    testImplementation("junit:junit:4.13.2")
}

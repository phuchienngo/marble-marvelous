import java.security.MessageDigest
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
            isMinifyEnabled = true
            isShrinkResources = true
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

    androidResources {
        // These assets are already compressed binary formats (ETC2 KTX textures,
        // precompiled Filament materials, packed mesh). Storing them uncompressed
        // barely changes APK size but lets them be opened as file descriptors so
        // they can be streamed straight into a native buffer without a transient
        // full-size heap copy at load time.
        noCompress += setOf("ktx", "filamat", "g3db")
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

// Compiles the Filament material sources (src/main/materials/*.mat) into the
// precompiled Vulkan/mobile packages shipped in assets (assets/filament/*.filamat)
// using the Filament `matc` tool, and records each source's checksum so the
// drift-detection unit test can catch a .mat edited without recompiling.
// Provide matc via -Pfilament.matc=/path/to/matc or the FILAMENT_MATC env var.
tasks.register("compileFilamentMaterials") {
    group = "filament"
    description = "Compiles src/main/materials/*.mat into assets/filament/*.filamat via matc."
    doLast {
        val matc: String =
            (project.findProperty("filament.matc") as String?)
                ?: System.getenv("FILAMENT_MATC")
                ?: throw GradleException(
                    "matc not found. Pass -Pfilament.matc=/path/to/matc or set FILAMENT_MATC."
                )
        val materialsDir = file("src/main/materials")
        val outputDir = file("src/main/assets/filament").apply { mkdirs() }
        val matFiles =
            materialsDir.listFiles { candidate -> candidate.extension == "mat" }
                ?.sortedBy { it.name }
                .orEmpty()
        val checksums = StringBuilder()
        matFiles.forEach { matFile ->
            val outputFile = outputDir.resolve(matFile.nameWithoutExtension + ".filamat")
            exec {
                commandLine(
                    matc,
                    "-a", "vulkan",
                    "-p", "mobile",
                    "-o", outputFile.absolutePath,
                    matFile.absolutePath
                )
            }
            val digest: String =
                MessageDigest
                    .getInstance("SHA-256")
                    .digest(matFile.readBytes())
                    .joinToString("") { hashByte: Byte -> "%02x".format(hashByte) }
            checksums.append("${matFile.name}=$digest\n")
        }
        materialsDir.resolve("checksums.sha256").writeText(checksums.toString())
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

    // --- Kotlin coroutines (background weather fetches) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // --- Dagger dependency graph for wallpaper runtime dependencies ---
    implementation("com.google.dagger:dagger:$daggerVersion")
    ksp("com.google.dagger:dagger-compiler:$daggerVersion")

    // --- OkHttp for OpenWeather cloud-tile downloads ---
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")

    testImplementation("junit:junit:4.13.2")
}

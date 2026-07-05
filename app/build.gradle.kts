import java.security.MessageDigest
import java.util.Properties
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.isFile) {
    localPropertiesFile.inputStream().use { inputStream ->
        localProperties.load(inputStream)
    }
}
val localOpenWeatherApiKey: String = localProperties.getProperty("OPENWEATHER_API_KEY", "")
val openWeatherApiKeyProvider = providers
    .gradleProperty("OPENWEATHER_API_KEY")
    .orElse(providers.environmentVariable("OPENWEATHER_API_KEY"))
    .orElse(localOpenWeatherApiKey)

android {
    namespace = "com.phuchienngo.marblemarvelous"
    compileSdk = 37

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
        resValues = true
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

abstract class CompileFilamentMaterialsTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Optional
    @get:Input
    abstract val matc: Property<String>

    @get:Internal
    abstract val materialsDir: DirectoryProperty

    @get:Internal
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun compile() {
        val matcPath: String =
            matc.orNull
                ?: throw GradleException(
                    "matc not found. Pass -Pfilament.matc=/path/to/matc or set FILAMENT_MATC."
                )
        val materialsDirFile = materialsDir.get().asFile
        val outputDirFile = outputDir.get().asFile.apply { mkdirs() }
        val matFiles =
            materialsDirFile.listFiles { candidate -> candidate.extension == "mat" }
                ?.sortedBy { it.name }
                .orEmpty()
        val checksums = StringBuilder()
        matFiles.forEach { matFile ->
            val outputFile = outputDirFile.resolve(matFile.nameWithoutExtension + ".filamat")
            execOperations.exec {
                commandLine(
                    matcPath,
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
        materialsDirFile.resolve("checksums.sha256").writeText(checksums.toString())
    }
}

tasks.register<CompileFilamentMaterialsTask>("compileFilamentMaterials") {
    group = "filament"
    description = "Compiles src/main/materials/*.mat into assets/filament/*.filamat via matc."
    matc.set(
        providers.gradleProperty("filament.matc")
            .orElse(providers.environmentVariable("FILAMENT_MATC"))
    )
    materialsDir.set(layout.projectDirectory.dir("src/main/materials"))
    outputDir.set(layout.projectDirectory.dir("src/main/assets/filament"))
}

dependencies {
    implementation("androidx.activity:activity:1.13.0")
    implementation("com.google.android.filament:filament-android:1.72.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.google.dagger:hilt-android:2.60")
    ksp("com.google.dagger:hilt-android-compiler:2.60")
    compileOnly("com.google.errorprone:error_prone_annotations:2.50.0")
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    testImplementation("junit:junit:4.13.2")
}

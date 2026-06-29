package com.phuchienngo.marblemarvelous

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SourceStructureTest {
    private val topLevelTypeRegex: Regex =
        Regex(
            "^(public |private |internal |abstract |sealed |data |open |enum |annotation |value )*" +
                "(class|interface|object|enum class|annotation class)\\b"
        )

    @Test
    fun sourceTreeUsesMarbleSpecificPackages() {
        val sourceRoot: File = File("src/main/kotlin/com/phuchienngo/marblemarvelous")
        val sourceFiles: List<File> =
            sourceRoot
                .walkTopDown()
                .filter { file: File ->
                    return@filter file.isFile
                }.toList()
        val sourceText: String =
            sourceFiles
                .joinToString(separator = "\n") { file: File ->
                    return@joinToString file.readText()
                }

        assertTrue(File(sourceRoot, "earth/EarthEngine.kt").exists())
        assertTrue(File(sourceRoot, "earth/EarthWallpaperService.kt").exists())
        assertTrue(File(sourceRoot, "earth/shader/EarthMask.kt").exists())
        assertTrue(File(sourceRoot, "earth/shader/attributes/EarthTextureAttribute.kt").exists())
        assertTrue(File(sourceRoot, "wallpaper/BaseWallpaperEngine.kt").exists())
        assertFalse(File(sourceRoot, "celestialBodies").exists())
        assertFalse(File(sourceRoot, "view").exists())
        assertFalse(sourceText.contains("celestialBodies"))
        assertFalse(sourceText.contains("PlanetMask"))
        assertFalse(sourceText.contains("PlanetTextureAttribute"))
    }

    @Test
    fun sourceTreeUsesKotlinSourceRoots() {
        assertTrue(File("src/main/kotlin/com/phuchienngo/marblemarvelous").exists())
        assertTrue(File("src/test/kotlin/com/phuchienngo/marblemarvelous").exists())
        assertFalse(File("src/main/java").exists())
        assertFalse(File("src/test/java").exists())
    }

    @Test
    fun sourceTreeDoesNotContainJavaOrProtoSources() {
        val roots: List<File> =
            listOf(
                File("src/main"),
                File("src/test")
            )
        val violations: List<String> =
            roots
                .flatMap { root: File ->
                    return@flatMap root.walkTopDown().filter sourceFileFilter@{ file: File ->
                        return@sourceFileFilter file.isFile && (file.extension == "java" || file.extension == "proto")
                    }
                }.map { sourceFile: File ->
                    return@map sourceFile.path
                }

        assertTrue(
            "Remove or convert Java/proto sources:\n${violations.joinToString(separator = "\n")}",
            violations.isEmpty()
        )
    }

    @Test
    fun permissionsActivityUsesJetpackCompose() {
        val buildSource: String = File("build.gradle.kts").readText()
        val permissionsActivitySource: String =
            File("src/main/kotlin/com/phuchienngo/marblemarvelous/permissions/PermissionsActivity.kt").readText()

        assertTrue(buildSource.contains("id(\"org.jetbrains.kotlin.plugin.compose\")"))
        assertTrue(buildSource.contains("compose = true"))
        assertTrue(buildSource.contains("androidx.activity:activity-compose"))
        assertTrue(permissionsActivitySource.contains("ComponentActivity"))
        assertTrue(permissionsActivitySource.contains("setContent"))
        assertTrue(permissionsActivitySource.contains("rememberLauncherForActivityResult"))
        assertFalse(permissionsActivitySource.contains("requestPermissions("))
    }

    @Test
    fun earthEngineDoesNotStartPermissionFlowFromWallpaperRender() {
        val earthEngineSource: String =
            File("src/main/kotlin/com/phuchienngo/marblemarvelous/earth/EarthEngine.kt").readText()

        assertTrue(earthEngineSource.contains("userLocation.lastKnown(requestPermissions = false)"))
        assertFalse(earthEngineSource.contains("userLocation.lastKnown(requestPermissions = true)"))
    }

    @Test
    fun liveWallpaperSettingsLaunchesForegroundPermissionFlow() {
        val manifestSource: String = File("src/main/AndroidManifest.xml").readText()
        val wallpaperMetadataSource: String = File("src/main/res/xml/live_wallpaper_earth.xml").readText()
        val permissionsActivitySource: String =
            File("src/main/kotlin/com/phuchienngo/marblemarvelous/permissions/PermissionsActivity.kt").readText()
        val permissionsActivityManifestEntry: String =
            Regex("<activity\\s+[^>]*android:name=\"com.phuchienngo.marblemarvelous.permissions.PermissionsActivity\"[^>]*>")
                .find(manifestSource)
                ?.value
                ?: ""

        assertTrue(
            wallpaperMetadataSource.contains(
                "android:settingsActivity=\"com.phuchienngo.marblemarvelous.permissions.PermissionsActivity\""
            )
        )
        assertTrue(permissionsActivityManifestEntry.contains("android:exported=\"true\""))
        assertTrue(permissionsActivitySource.contains("LocationPermissions.LOCATION_PERMISSION"))
        assertTrue(permissionsActivitySource.contains("LocationPermissions.LOCATION_KEY"))
    }

    @Test
    fun assetsUseMarbleSpecificShaderDirectory() {
        val assetsRoot: File = File("src/main/assets")

        assertTrue(File(assetsRoot, "marble/earthMask.frag").exists())
        assertTrue(File(assetsRoot, "marble/earthMask.vert").exists())
        assertFalse(File(assetsRoot, "celestialbodies").exists())
    }

    @Test
    fun eachSourceFileHasOnlyOneTopLevelType() {
        val roots: List<File> =
            listOf(
                File("src/main/kotlin/com/phuchienngo/marblemarvelous"),
                File("src/test/kotlin/com/phuchienngo/marblemarvelous")
            )
        val violations: List<String> =
            roots
                .flatMap { root: File ->
                    return@flatMap sourceFiles(root)
                }.mapNotNull { sourceFile: File ->
                    val typeCount: Int = countTopLevelTypes(sourceFile)
                    if (typeCount > 1) {
                        return@mapNotNull "${sourceFile.path} has $typeCount top-level types"
                    }
                    return@mapNotNull null
                }

        assertTrue(
            "Move extra top-level types into their own files:\n${violations.joinToString(separator = "\n")}",
            violations.isEmpty()
        )
    }

    private fun sourceFiles(root: File): List<File> =
        root
            .walkTopDown()
            .filter { file: File ->
                return@filter file.isFile && (file.extension == "kt" || file.extension == "java")
            }.toList()

    private fun countTopLevelTypes(sourceFile: File): Int =
        sourceFile
            .readLines()
            .count { line: String ->
                return@count topLevelTypeRegex.containsMatchIn(line)
            }
}

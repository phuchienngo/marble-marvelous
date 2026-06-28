package com.phuchienngo.marblemarvelous.di

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EngineDependencyInjectionTest {
    @Test
    fun inputMultiplexerIsInjectedIntoWallpaperEngines() {
        val inputMultiplexerSource: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/input/InputMultiplexer.kt",
            )
        val userAwareEngineSource: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/wallpaper/UserAwareWallpaperService.kt",
            )
        val baseEngineSource: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/wallpaper/BaseWallpaperEngine.kt",
            )

        assertTrue(inputMultiplexerSource.contains("@WallpaperEngineScope"))
        assertTrue(inputMultiplexerSource.contains("class InputMultiplexer"))
        assertTrue(inputMultiplexerSource.contains("@Inject"))
        assertTrue(inputMultiplexerSource.contains("constructor() : InputProcessor"))
        assertFalse(userAwareEngineSource.contains("InputMultiplexer()"))
        assertTrue(userAwareEngineSource.contains("private val multiplexer: InputMultiplexer"))
        assertTrue(baseEngineSource.contains("UserAwareEngine(inputMultiplexer)"))
    }

    @Test
    fun fpsThrottlerIsInjectedIntoWallpaperEngines() {
        val fpsThrottlerSource: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/power/FPSThrottler.kt",
            )
        val baseEngineSource: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/wallpaper/BaseWallpaperEngine.kt",
            )
        val earthEngineSource: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/earth/EarthEngine.kt",
            )

        assertTrue(fpsThrottlerSource.contains("@WallpaperEngineScope"))
        assertTrue(fpsThrottlerSource.contains("class FPSThrottler"))
        assertTrue(fpsThrottlerSource.contains("@Inject"))
        assertFalse(baseEngineSource.contains("FPSThrottler()"))
        assertTrue(baseEngineSource.contains("private val fpsThrottler: FPSThrottler"))
        assertTrue(earthEngineSource.contains("BaseWallpaperEngine(inputMultiplexer, fpsThrottler)"))
    }

    @Test
    fun unusedPlanetWallpapersAreNotRegistered() {
        val manifestSource: String = readSource("src/main/AndroidManifest.xml")
        val engineComponentSource: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/di/EngineComponent.kt",
            )

        assertFalse(manifestSource.contains("MoonWallpaperService"))
        assertFalse(manifestSource.contains("live_wallpaper_moon"))
        assertFalse(manifestSource.contains("PlutoWallpaperService"))
        assertFalse(manifestSource.contains("live_wallpaper_pluto"))
        assertFalse(manifestSource.contains("MarsWallpaperService"))
        assertFalse(engineComponentSource.contains("moonEngine"))
        assertFalse(engineComponentSource.contains("plutoEngine"))
        assertFalse(engineComponentSource.contains("marsEngine"))
        assertFalse(
            File("src/main/java/com/phuchienngo/marblemarvelous/earth/wallpapers/variations/MoonWallpaperService.kt").exists(),
        )
        assertFalse(
            File("src/main/java/com/phuchienngo/marblemarvelous/earth/wallpapers/variations/PlutoWallpaperService.kt").exists(),
        )
        assertFalse(File("src/main/java/com/phuchienngo/marblemarvelous/earth/shader/MoonShader.kt").exists())
        assertFalse(File("src/main/java/com/phuchienngo/marblemarvelous/earth/shader/PlutoShader.kt").exists())
        assertFalse(File("src/main/java/com/phuchienngo/marblemarvelous/earth/PlanetEngine.kt").exists())
        assertFalse(File("src/main/java/com/phuchienngo/marblemarvelous/earth/wallpapers/PlanetWallpaper.kt").exists())
    }

    private fun readSource(path: String): String = File(path).readText()
}

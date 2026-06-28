package com.phuchienngo.marblemarvelous.di

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EngineDependencyInjectionTest {
    @Test
    fun moonWallpaperServiceUsesEngineComponentFactory() {
        val source: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/celestialBodies/wallpapers/variations/MoonWallpaperService.kt",
            )

        assertTrue(source.contains("DaggerEngineComponent"))
        assertTrue(source.contains(".factory()"))
        assertFalse(source.contains("return MoonEngine(app)"))
    }

    @Test
    fun plutoWallpaperServiceUsesEngineComponentFactory() {
        val source: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/celestialBodies/wallpapers/variations/PlutoWallpaperService.kt",
            )

        assertTrue(source.contains("DaggerEngineComponent"))
        assertTrue(source.contains(".factory()"))
        assertFalse(source.contains("return PlutoEngine(app)"))
    }

    @Test
    fun inputMultiplexerIsInjectedIntoWallpaperEngines() {
        val inputMultiplexerSource: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/input/InputMultiplexer.kt",
            )
        val userAwareEngineSource: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/view/UserAwareWallpaperService.kt",
            )
        val baseEngineSource: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/view/BaseWallpaperEngine.kt",
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
                "src/main/java/com/phuchienngo/marblemarvelous/view/BaseWallpaperEngine.kt",
            )
        val earthEngineSource: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/celestialBodies/EarthEngine.kt",
            )
        val planetEngineSource: String =
            readSource(
                "src/main/java/com/phuchienngo/marblemarvelous/celestialBodies/PlanetEngine.kt",
            )

        assertTrue(fpsThrottlerSource.contains("@WallpaperEngineScope"))
        assertTrue(fpsThrottlerSource.contains("class FPSThrottler"))
        assertTrue(fpsThrottlerSource.contains("@Inject"))
        assertFalse(baseEngineSource.contains("FPSThrottler()"))
        assertTrue(baseEngineSource.contains("private val fpsThrottler: FPSThrottler"))
        assertTrue(earthEngineSource.contains("BaseWallpaperEngine(inputMultiplexer, fpsThrottler)"))
        assertTrue(planetEngineSource.contains("BaseWallpaperEngine(inputMultiplexer, fpsThrottler)"))
    }

    private fun readSource(path: String): String = File(path).readText()
}

package com.phuchienngo.marblemarvelous.power

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ResumeRenderWarmupTest {
    @Test
    fun resumeMarksShortHighFpsWarmupWindow() {
        val source: String = readBaseWallpaperEngineSource()

        assertTrue(source.contains("resumeRenderWarmup.markResumed()"))
        assertTrue(source.contains("protected fun isResumeWarmupActive(): Boolean = resumeRenderWarmup.isActive()"))
    }

    @Test
    fun earthRenderUsesWarmupForHighFpsAndDefersNonInitialCloudWork() {
        val source: String = readEarthEngineSource()

        assertTrue(source.contains("isResumeWarmupActive() ||"))
        assertTrue(source.contains("cloudsTexture == null || !isResumeWarmupActive()"))
        assertTrue(source.contains("pendingResumeCloudRefresh = true"))
        assertTrue(source.contains("maybeUpdateCloudsAfterResumeWarmup()"))
        assertFalse(source.contains("cloudsProvider!!.updateClouds()"))
    }

    private fun readBaseWallpaperEngineSource(): String =
        File("src/main/kotlin/com/phuchienngo/marblemarvelous/wallpaper/BaseWallpaperEngine.kt")
            .readText()

    private fun readEarthEngineSource(): String =
        File("src/main/kotlin/com/phuchienngo/marblemarvelous/earth/EarthEngine.kt")
            .readText()
}

package com.phuchienngo.marblemarvelous.weather

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OpenWeatherCloudsTest {
    @Test
    fun cloudRefreshDoesNotPrefetchEveryTileBeforeRendering() {
        val source: String =
            File("src/main/kotlin/com/phuchienngo/marblemarvelous/weather/OpenWeatherClouds.kt")
                .readText()

        assertFalse(source.contains("downloadCloudTiles(apiKey, tileDirectory)"))
    }

    @Test
    fun cloudRefreshDoesNotForceProcessWideGcAfterTextureUpload() {
        val source: String =
            File("src/main/kotlin/com/phuchienngo/marblemarvelous/earth/EarthEngine.kt")
                .readText()

        assertFalse(source.contains("System.gc()"))
    }

    @Test
    fun getCloudValueReturnsLumaForOpaquePixels() {
        assertEquals(OPAQUE_WHITE_CLOUD, OpenWeatherClouds.getCloudValue(WHITE))
        assertEquals(NO_CLOUD, OpenWeatherClouds.getCloudValue(BLACK))
    }

    @Test
    fun getCloudValueScalesDownBySemiTransparency() {
        // 0x80404040: alpha 128, luma 63 (float-truncated) -> (128*63)/255 = 31.
        assertEquals(SUBTLE_DARK_CLOUD, OpenWeatherClouds.getCloudValue(SEMI_TRANSPARENT_DARK))
    }

    @Test
    fun smoothCloudFaceSoftensSingleBrightCloudPixel() {
        val pixels: IntArray =
            intArrayOf(
                BLACK,
                BLACK,
                BLACK,
                BLACK,
                WHITE,
                BLACK,
                BLACK,
                BLACK,
                BLACK
            )

        val actual: IntArray = OpenWeatherClouds.smoothCloudFace(pixels, width = 3, height = 3)

        assertEquals(CENTER_SOFTENED, actual[4])
        assertEquals(NEIGHBOR_SOFTENED, actual[1])
        assertEquals(CORNER_SOFTENED, actual[0])
    }

    @Test
    fun tiledCloudSourceSamplesAcrossTileBoundaries() =
        runBlocking {
            val tileDirectory: File = createTileDirectory()
            writeTile(tileDirectory, x = 0, y = 0, values = byteArrayOf(0, 10, 40, 50))
            writeTile(tileDirectory, x = 1, y = 0, values = byteArrayOf(20, 30, 60, 70))
            writeTile(tileDirectory, x = 0, y = 1, values = byteArrayOf(80, 90, 120, 130.toByte()))
            writeTile(tileDirectory, x = 1, y = 1, values = byteArrayOf(100, 110, 140.toByte(), 150.toByte()))
            val source: OpenWeatherClouds.TiledCloudSource =
                OpenWeatherClouds.TiledCloudSource(
                    tileDirectory = tileDirectory,
                    tilesPerAxis = 2,
                    tileSize = 2,
                    maxCachedTiles = 2,
                    tileLoader = null
                )

            val acrossXAndY: Int? = source.sample(sourceX = 1.5, sourceY = 1.5)
            val wrappedAcrossDateLine: Int? = source.sample(sourceX = 3.5, sourceY = 0.0)

            assertEquals(BOUNDARY_CLOUD, acrossXAndY)
            assertEquals(WRAPPED_CLOUD, wrappedAcrossDateLine)
            return@runBlocking
        }

    @Test
    fun tiledCloudSourceLoadsMissingTileOnlyWhenSampled() =
        runBlocking {
            val tileDirectory: File = createTileDirectory()
            val requestedTiles: MutableList<String> = mutableListOf()
            val source: OpenWeatherClouds.TiledCloudSource =
                OpenWeatherClouds.TiledCloudSource(
                    tileDirectory = tileDirectory,
                    tilesPerAxis = 2,
                    tileSize = 2,
                    maxCachedTiles = 2,
                    tileLoader =
                        OpenWeatherClouds.TileLoader loadTestTile@{ x: Int, y: Int ->
                            requestedTiles.add("$x-$y")
                            return@loadTestTile byteArrayOf(
                                LOADED_CLOUD.toByte(),
                                LOADED_CLOUD.toByte(),
                                LOADED_CLOUD.toByte(),
                                LOADED_CLOUD.toByte()
                            )
                        }
                )

            val firstSample: Int? = source.sample(sourceX = 0.0, sourceY = 0.0)
            val secondSample: Int? = source.sample(sourceX = 0.5, sourceY = 0.5)

            assertEquals(LOADED_CLOUD, firstSample)
            assertEquals(LOADED_CLOUD, secondSample)
            assertEquals(listOf("0-0"), requestedTiles)
            assertTrue(OpenWeatherClouds.tileFile(tileDirectory, x = 0, y = 0).exists())
            return@runBlocking
        }

    @Test
    fun tiledCloudSourceEvictsTilesAboveCacheLimit() =
        runBlocking {
            val tileDirectory: File = createTileDirectory()
            writeTile(tileDirectory, x = 0, y = 0, values = byteArrayOf(0, 0, 0, 0))
            writeTile(tileDirectory, x = 1, y = 0, values = byteArrayOf(10, 10, 10, 10))
            writeTile(tileDirectory, x = 0, y = 1, values = byteArrayOf(20, 20, 20, 20))
            writeTile(tileDirectory, x = 1, y = 1, values = byteArrayOf(30, 30, 30, 30))
            val source: OpenWeatherClouds.TiledCloudSource =
                OpenWeatherClouds.TiledCloudSource(
                    tileDirectory = tileDirectory,
                    tilesPerAxis = 2,
                    tileSize = 2,
                    maxCachedTiles = 2,
                    tileLoader = null
                )

            source.sample(sourceX = 0.0, sourceY = 0.0)
            source.sample(sourceX = 2.0, sourceY = 0.0)
            source.sample(sourceX = 0.0, sourceY = 2.0)

            assertTrue(source.cachedTileCount() <= 2)
            return@runBlocking
        }

    private fun createTileDirectory(): File {
        val directory: File =
            File(
                System.getProperty("java.io.tmpdir"),
                "openweather-clouds-test-${System.nanoTime()}"
            )
        directory.mkdirs()
        return directory
    }

    private fun writeTile(
        directory: File,
        x: Int,
        y: Int,
        values: ByteArray
    ) {
        OpenWeatherClouds
            .tileFile(directory, x, y)
            .writeBytes(values)
    }

    companion object {
        private const val BLACK: Int = -0x1000000
        private const val WHITE: Int = -0x1
        private const val SEMI_TRANSPARENT_DARK: Int = 0x80404040.toInt()

        // Cloud values (0..255), not ARGB.
        private const val OPAQUE_WHITE_CLOUD: Int = 255
        private const val NO_CLOUD: Int = 0
        private const val SUBTLE_DARK_CLOUD: Int = 31
        private const val BOUNDARY_CLOUD: Int = 75
        private const val WRAPPED_CLOUD: Int = 15
        private const val LOADED_CLOUD: Int = 42

        // Center-heavy 2-8-2 kernel (weight 20): white center on black neighbours ->
        // center=(255*8+10)/20=102 (0x66), edge=(255*2+10)/20=26 (0x1a), corner=13 (0x0d).
        private const val CENTER_SOFTENED: Int = -0x99999a
        private const val NEIGHBOR_SOFTENED: Int = -0xe5e5e6
        private const val CORNER_SOFTENED: Int = -0xf2f2f3
    }
}

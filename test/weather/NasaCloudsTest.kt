package com.phuchienngo.marblemarvelous.weather

import com.phuchienngo.marblemarvelous.space.AuroraActivityProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.io.File
import java.io.RandomAccessFile

class NasaCloudsTest {
  @Test
  fun choosesLatestCompleteUtcDate() {
    assertEquals(
      LocalDate.of(2026, 7, 11),
      NasaClouds.latestCompleteDate(LocalDate.of(2026, 7, 12))
    )
  }

  @Test
  fun reusesOnlyCompleteCloudCacheFromSameObservationDate() {
    val cacheDirectory: File = createCacheDirectory()
    NasaClouds.cloudCacheDateFile(cacheDirectory).writeText("2026-07-11")
    for (face: String in FACE_NAMES) {
      RandomAccessFile(File(cacheDirectory, "$face-nasa-v5.r8"), "rw").use cacheFace@{ file: RandomAccessFile ->
        file.setLength(EXPECTED_FACE_BYTES)
        return@cacheFace
      }
    }

    assertTrue(NasaClouds.isCacheCurrent(cacheDirectory, LocalDate.of(2026, 7, 11)))
    assertFalse(NasaClouds.isCacheCurrent(cacheDirectory, LocalDate.of(2026, 7, 10)))
    File(cacheDirectory, "px-nasa-v5.r8").delete()
    assertFalse(NasaClouds.isCacheCurrent(cacheDirectory, LocalDate.of(2026, 7, 11)))
  }

  @Test
  fun buildsKeylessNasaGibsCloudFractionUri() {
    val uri: java.net.URI =
      NasaClouds.cloudImageUri(
        date = LocalDate.of(2026, 7, 11),
        layer = "MODIS_Aqua_Cloud_Fraction_Day"
      )

    assertEquals("https", uri.scheme)
    assertEquals("gibs.earthdata.nasa.gov", uri.host)
    assertEquals("/wms/epsg4326/best/wms.cgi", uri.path)
    assertEquals(
      "SERVICE=WMS&REQUEST=GetMap&VERSION=1.1.1" +
        "&LAYERS=MODIS_Aqua_Cloud_Fraction_Day&STYLES=default" +
        "&FORMAT=image/png&TRANSPARENT=true&SRS=EPSG:4326" +
        "&BBOX=-180,-90,180,90&WIDTH=4096&HEIGHT=2048&TIME=2026-07-11",
      uri.rawQuery
    )
  }

  @Test
  fun decodesNasaCloudFractionColormapAndRejectsNoData() {
    assertEquals(0, NasaClouds.decodeCloudPercentage(LOW_CLOUD_COLOR))
    assertEquals(50, NasaClouds.decodeCloudPercentage(MEDIUM_CLOUD_COLOR))
    assertEquals(100, NasaClouds.decodeCloudPercentage(FULL_CLOUD_COLOR))
    assertNull(NasaClouds.decodeCloudPercentage(TRANSPARENT_NO_DATA))
    assertNull(NasaClouds.decodeCloudPercentage(UNKNOWN_OPAQUE_COLOR))
  }

  @Test
  fun mergeKeepsFirstObservationAndOnlyFillsMissingPixels() {
    val encodedClouds: ShortArray = shortArrayOf(0, EXISTING_CLOUD_ENCODED, 0)
    val nasaPixels: IntArray =
      intArrayOf(FULL_CLOUD_COLOR, MEDIUM_CLOUD_COLOR, TRANSPARENT_NO_DATA)

    val newlyCovered: Int = NasaClouds.mergeCloudRow(encodedClouds, nasaPixels, rowOffset = 0)

    assertEquals(1, newlyCovered)
    assertEquals(FULL_CLOUD_ENCODED, encodedClouds[0])
    assertEquals(EXISTING_CLOUD_ENCODED, encodedClouds[1])
    assertEquals(NO_DATA_ENCODED, encodedClouds[2])
  }

  @Test
  fun fillsRemainingSwathGapsFromNearestObservedPixels() {
    val encodedClouds: ShortArray =
      shortArrayOf(
        0,
        LOW_CLOUD_ENCODED,
        0,
        0,
        0,
        FULL_CLOUD_ENCODED
      )

    assertTrue(NasaClouds.fillMissingPixels(encodedClouds, width = 3, height = 2))
    assertEquals(
      listOf(
        LOW_CLOUD_ENCODED,
        LOW_CLOUD_ENCODED,
        LOW_CLOUD_ENCODED,
        FULL_CLOUD_ENCODED,
        FULL_CLOUD_ENCODED,
        FULL_CLOUD_ENCODED
      ),
      encodedClouds.toList()
    )
  }

  @Test
  fun samplesEquirectangularCloudSourceAtPixelCenters() {
    val source: NasaClouds.EquirectangularCloudSource =
      NasaClouds.EquirectangularCloudSource(
        width = 4,
        height = 2,
        encodedClouds =
          shortArrayOf(
            encodeCloudByte(10),
            encodeCloudByte(20),
            encodeCloudByte(30),
            encodeCloudByte(40),
            encodeCloudByte(50),
            encodeCloudByte(60),
            encodeCloudByte(70),
            encodeCloudByte(80)
          )
      )

    assertEquals(10, source.sample(latDeg = 45.0, lonDeg = -135.0))
    assertEquals(80, source.sample(latDeg = -45.0, lonDeg = 135.0))
  }

  @Test
  fun retriesOnlyTransientHttpStatuses() {
    assertTrue(NasaClouds.isRetriableStatusCode(408))
    assertTrue(NasaClouds.isRetriableStatusCode(429))
    assertTrue(NasaClouds.isRetriableStatusCode(500))
    assertFalse(NasaClouds.isRetriableStatusCode(401))
    assertFalse(NasaClouds.isRetriableStatusCode(403))
    assertFalse(NasaClouds.isRetriableStatusCode(404))
  }

  @Test
  fun smoothCloudRowsFeathersEdgesWithoutFillingEmptySpace() {
    val emptyRow: ByteArray = byteArrayOf(0, 0, 0, 0, 0)
    val currentRow: ByteArray = byteArrayOf(0, 0, STRONG_CLOUD.toByte(), 0, 0)
    val outputRow: ByteArray = ByteArray(currentRow.size)

    NasaClouds.smoothCloudRows(
      previousRow = emptyRow,
      currentRow = currentRow,
      nextRow = emptyRow,
      outputRow = outputRow
    )

    val emptyLeft: Int = outputRow[0].toInt() and 0xFF
    val featheredEdge: Int = outputRow[1].toInt() and 0xFF
    val softCore: Int = outputRow[2].toInt() and 0xFF

    assertEquals(NO_CLOUD, emptyLeft)
    assertTrue(featheredEdge > SOFT_EDGE_MINIMUM)
    assertTrue(softCore > SOFT_CORE_MINIMUM)
    assertTrue(softCore > featheredEdge)
    assertTrue(softCore > featheredEdge * MINIMUM_CORE_TO_EDGE_RATIO)
  }

  @Test
  fun parsesObjectBasedAuroraFeedWithPlatformJson() {
    val json: String = """[{"time_tag":"header"},{"Kp":4.5}]"""

    assertEquals(0.575f, requireNotNull(AuroraActivityProvider.parseActivity(json)), 0.0001f)
  }

  @Test
  fun parsesLegacyAuroraFeedWithPlatformJson() {
    val json: String = """[["time_tag","Kp"],["2026-07-12", "9"]]"""

    assertEquals(1.0f, requireNotNull(AuroraActivityProvider.parseActivity(json)), 0.0001f)
  }

  private fun encodeCloudByte(cloud: Int): Short = (cloud + ENCODED_OFFSET).toShort()

  private fun createCacheDirectory(): File {
    val directory: File =
      File(
        System.getProperty("java.io.tmpdir"),
        "nasa-cloud-cache-test-${System.nanoTime()}"
      )
    directory.mkdirs()
    return directory
  }

  companion object {
    private const val LOW_CLOUD_COLOR: Int = 0xFF660077.toInt()
    private const val MEDIUM_CLOUD_COLOR: Int = 0xFF00DC00.toInt()
    private const val FULL_CLOUD_COLOR: Int = 0xFFFF0005.toInt()
    private const val TRANSPARENT_NO_DATA: Int = 0x00C0C0C0
    private const val UNKNOWN_OPAQUE_COLOR: Int = 0xFF123456.toInt()

    private const val NO_DATA_ENCODED: Short = 0
    private const val LOW_CLOUD_ENCODED: Short = 1
    private const val EXISTING_CLOUD_ENCODED: Short = 65
    private const val FULL_CLOUD_ENCODED: Short = 256
    private const val ENCODED_OFFSET: Int = 1

    private const val NO_CLOUD: Int = 0
    private const val STRONG_CLOUD: Int = 180
    private const val SOFT_EDGE_MINIMUM: Int = 40
    private const val SOFT_CORE_MINIMUM: Int = 100
    private const val MINIMUM_CORE_TO_EDGE_RATIO: Int = 3
    private const val EXPECTED_FACE_BYTES: Long = 1024L * 1024L
    private val FACE_NAMES: Array<String> = arrayOf("px", "nx", "py", "ny", "pz", "nz")
  }
}

package com.phuchienngo.marblemarvelous.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URI
import java.time.LocalDate
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.HttpsURLConnection
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Builds the six cached R8 cloud faces from public NASA GIBS MODIS imagery. */
@Singleton
internal class NasaClouds
@Inject
constructor() {
  private val generationMutex: Mutex = Mutex()

  suspend fun generateCubeFaces(context: Context): Boolean =
    generationMutex.withLock generationLock@{
      return@generationLock withContext(Dispatchers.IO) ioContext@{
        return@ioContext generateCubeFacesOnce(context)
      }
    }

  private suspend fun generateCubeFacesOnce(context: Context): Boolean {
    val encodedClouds: ShortArray = ShortArray(SOURCE_PIXEL_COUNT)
    val observationDate: LocalDate = latestCompleteDate(LocalDate.now(ZoneOffset.UTC))
    if (isCacheCurrent(context.cacheDir, observationDate)) {
      Log.i(TAG, "Reusing complete NASA cloud cache for $observationDate")
      return false
    }
    var coveredPixels = 0

    dates@ for (daysAgo: Long in 0L..MAX_LOOKBACK_DAYS) {
      val date: LocalDate = observationDate.minusDays(daysAgo)
      for (layer: String in CLOUD_LAYERS) {
        val bitmap: Bitmap = downloadCloudImage(date, layer) ?: continue
        try {
          coveredPixels += mergeCloudBitmap(encodedClouds, bitmap)
        } finally {
          bitmap.recycle()
        }
        Log.i(TAG, "Merged $layer for $date (${coveragePercent(coveredPixels)}% coverage)")
        if (hasCoverage(coveredPixels, TARGET_COVERAGE_PERMILLE)) {
          break@dates
        }
      }
    }

    if (!hasCoverage(coveredPixels, MINIMUM_COVERAGE_PERMILLE)) {
      Log.w(TAG, "NASA cloud coverage too sparse: ${coveragePercent(coveredPixels)}%")
      return false
    }
    if (!fillMissingPixels(encodedClouds, SOURCE_WIDTH, SOURCE_HEIGHT)) {
      Log.w(TAG, "NASA cloud image contains no usable pixels")
      return false
    }

    val source: EquirectangularCloudSource =
      EquirectangularCloudSource(
        width = SOURCE_WIDTH,
        height = SOURCE_HEIGHT,
        encodedClouds = encodedClouds
      )
    for (faceIndex: Int in FACES.indices) {
      val wroteFace: Boolean =
        writeRawFace(
          faceIndex = faceIndex,
          source = source,
          dest = File(
            context.cacheDir,
            FACES[faceIndex] + RAW_FACE_VERSION + RAW_FACE_EXTENSION
          )
        )
      if (!wroteFace) {
        return false
      }
    }
    deleteLegacyFaces(context.cacheDir)
    writeCacheDate(context.cacheDir, observationDate)
    return true
  }

  private suspend fun downloadCloudImage(
    date: LocalDate,
    layer: String
  ): Bitmap? {
    val uri: URI = cloudImageUri(date, layer)
    for (attempt: Int in 0 until DOWNLOAD_ATTEMPTS) {
      val result: CloudImageAttempt = downloadCloudImageOnce(uri, date, layer, attempt)
      if (result.bitmap != null) {
        return result.bitmap
      }
      if (!result.shouldRetry) {
        return null
      }
      if (attempt + 1 < DOWNLOAD_ATTEMPTS) {
        delay(DOWNLOAD_RETRY_DELAY)
      }
    }
    return null
  }

  private fun downloadCloudImageOnce(
    uri: URI,
    date: LocalDate,
    layer: String,
    attempt: Int
  ): CloudImageAttempt {
    var connection: HttpsURLConnection? = null
    return try {
      connection = uri.toURL().openConnection() as HttpsURLConnection
      connection.connectTimeout = HTTP_TIMEOUT_MILLIS
      connection.readTimeout = HTTP_TIMEOUT_MILLIS
      connection.requestMethod = HTTP_GET_METHOD
      val statusCode: Int = connection.responseCode
      if (statusCode !in SUCCESS_STATUS_RANGE) {
        Log.w(TAG, "$layer for $date -> HTTP $statusCode (attempt ${attempt + 1})")
        return CloudImageAttempt(bitmap = null, shouldRetry = isRetriableStatusCode(statusCode))
      }
      val options: BitmapFactory.Options = BitmapFactory.Options()
      options.inPreferredConfig = Bitmap.Config.ARGB_8888
      val bitmap: Bitmap? =
        connection.inputStream.use decodeImage@{ inputStream: InputStream ->
          return@decodeImage BitmapFactory.decodeStream(inputStream, null, options)
        }
      if (bitmap == null) {
        Log.w(TAG, "NASA returned a non-image response for $layer on $date")
        return CloudImageAttempt(bitmap = null, shouldRetry = false)
      }
      if (bitmap.width != SOURCE_WIDTH || bitmap.height != SOURCE_HEIGHT) {
        Log.w(TAG, "NASA image has unexpected size ${bitmap.width}x${bitmap.height}")
        bitmap.recycle()
        return CloudImageAttempt(bitmap = null, shouldRetry = false)
      }
      CloudImageAttempt(bitmap = bitmap, shouldRetry = false)
    } catch (e: Exception) {
      Log.e(TAG, "Failed downloading $layer for $date (attempt ${attempt + 1})", e)
      CloudImageAttempt(bitmap = null, shouldRetry = true)
    } finally {
      connection?.disconnect()
    }
  }

  private fun mergeCloudBitmap(
    encodedClouds: ShortArray,
    bitmap: Bitmap
  ): Int {
    val rowPixels: IntArray = IntArray(SOURCE_WIDTH)
    var newlyCovered = 0
    for (row: Int in 0 until SOURCE_HEIGHT) {
      bitmap.getPixels(rowPixels, 0, SOURCE_WIDTH, 0, row, SOURCE_WIDTH, 1)
      newlyCovered += mergeCloudRow(encodedClouds, rowPixels, row * SOURCE_WIDTH)
    }
    return newlyCovered
  }

  private fun writeRawFace(
    faceIndex: Int,
    source: EquirectangularCloudSource,
    dest: File
  ): Boolean {
    val parent: File = dest.parentFile ?: return false
    val tempDest: File = File(parent, dest.name + TEMP_RAW_FACE_EXTENSION)
    tempDest.delete()
    return try {
      FileOutputStream(tempDest).use writeTempFace@{ outputStream: FileOutputStream ->
        writeSmoothedRawFace(faceIndex, source, outputStream)
        outputStream.fd.sync()
        return@writeTempFace
      }
      if (!tempDest.renameTo(dest)) {
        tempDest.delete()
        return false
      }
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed writing $dest", e)
      tempDest.delete()
      false
    }
  }

  private fun writeSmoothedRawFace(
    faceIndex: Int,
    source: EquirectangularCloudSource,
    outputStream: FileOutputStream
  ) {
    var previousRow: ByteArray? = null
    var currentRow: ByteArray = sampleCloudRow(faceIndex, py = 0, source)
    var nextRow: ByteArray? =
      if (FACE > 1) {
        sampleCloudRow(faceIndex, py = 1, source)
      } else {
        null
      }
    val smoothedRow: ByteArray = ByteArray(FACE)

    for (py: Int in 0 until FACE) {
      smoothCloudRows(
        previousRow = previousRow ?: currentRow,
        currentRow = currentRow,
        nextRow = nextRow ?: currentRow,
        outputRow = smoothedRow
      )
      outputStream.write(smoothedRow)

      previousRow = currentRow
      currentRow = nextRow ?: currentRow
      val nextY: Int = py + 2
      nextRow =
        if (nextY < FACE) {
          sampleCloudRow(faceIndex, nextY, source)
        } else {
          null
        }
    }
  }

  private fun sampleCloudRow(
    faceIndex: Int,
    py: Int,
    source: EquirectangularCloudSource
  ): ByteArray {
    val row: ByteArray = ByteArray(FACE)
    val t: Double = 2.0 * (py + 0.5) / FACE - 1.0
    for (px: Int in 0 until FACE) {
      val s: Double = 2.0 * (px + 0.5) / FACE - 1.0
      var dx: Double
      var dy: Double
      var dz: Double
      when (faceIndex) {
        0 -> {
          dx = 1.0
          dy = -t
          dz = -s
        }

        1 -> {
          dx = -1.0
          dy = -t
          dz = s
        }

        2 -> {
          dx = s
          dy = 1.0
          dz = t
        }

        3 -> {
          dx = s
          dy = -1.0
          dz = -t
        }

        4 -> {
          dx = s
          dy = -t
          dz = 1.0
        }

        else -> {
          dx = -s
          dy = -t
          dz = -1.0
        }
      }
      val length: Double = sqrt(dx * dx + dy * dy + dz * dz)
      dx /= length
      dy /= length
      dz /= length
      val latDeg: Double = Math.toDegrees(asin(max(-1.0, min(1.0, dy))))
      var lonDeg: Double = Math.toDegrees(atan2(dx, dz))
      lonDeg = ((lonDeg + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
      row[px] = source.sample(latDeg, lonDeg).toByte()
    }
    return row
  }

  internal class EquirectangularCloudSource(
    private val width: Int,
    private val height: Int,
    private val encodedClouds: ShortArray
  ) {
    init {
      require(width > 0)
      require(height > 0)
      require(encodedClouds.size == width * height)
    }

    fun sample(
      latDeg: Double,
      lonDeg: Double
    ): Int {
      val sourceX: Double = (lonDeg + 180.0) / 360.0 * width - 0.5
      val sourceY: Double = (90.0 - latDeg.coerceIn(-90.0, 90.0)) / 180.0 * height - 0.5
      val wrappedX: Double = wrapHorizontal(sourceX, width)
      val clampedY: Double = sourceY.coerceIn(0.0, (height - 1).toDouble())
      val x0: Int = floor(wrappedX).toInt()
      val y0: Int = floor(clampedY).toInt()
      val x1: Int = (x0 + 1) % width
      val y1: Int = minOf(y0 + 1, height - 1)
      val xWeight: Double = wrappedX - x0
      val yWeight: Double = clampedY - y0

      val top: Double = lerp(valueAt(x0, y0), valueAt(x1, y0), xWeight)
      val bottom: Double = lerp(valueAt(x0, y1), valueAt(x1, y1), xWeight)
      return lerp(top, bottom, yWeight)
        .roundToInt()
        .coerceIn(0, MAX_CLOUD_BYTE)
    }

    private fun valueAt(
      x: Int,
      y: Int
    ): Double = (encodedClouds[y * width + x].toInt() - ENCODED_OFFSET).toDouble()
  }

  private class CloudImageAttempt(
    val bitmap: Bitmap?,
    val shouldRetry: Boolean
  )

  companion object {
    private const val TAG: String = "NasaClouds"
    private const val WMS_BASE_URL: String =
      "https://gibs.earthdata.nasa.gov/wms/epsg4326/best/wms.cgi"
    private const val SOURCE_WIDTH: Int = 4096
    private const val SOURCE_HEIGHT: Int = 2048
    private const val SOURCE_PIXEL_COUNT: Int = SOURCE_WIDTH * SOURCE_HEIGHT
    private const val FACE: Int = 1024
    private const val MAX_LOOKBACK_DAYS: Long = 1L
    private const val TARGET_COVERAGE_PERMILLE: Int = 995
    private const val MINIMUM_COVERAGE_PERMILLE: Int = 950
    private const val COVERAGE_SCALE: Int = 1000

    private const val RAW_FACE_VERSION: String = "-nasa-v5"
    private const val RAW_FACE_EXTENSION: String = ".r8"
    private const val TEMP_RAW_FACE_EXTENSION: String = ".tmp"
    private const val CACHE_DATE_FILE_NAME: String = "nasa-cloud-date-v5.txt"
    private const val DOWNLOAD_ATTEMPTS: Int = 3
    private val DOWNLOAD_RETRY_DELAY: Duration = 1.5.seconds
    private const val HTTP_TIMEOUT_MILLIS: Int = 20_000
    private const val HTTP_GET_METHOD: String = "GET"
    private const val HTTP_REQUEST_TIMEOUT: Int = 408
    private const val HTTP_TOO_MANY_REQUESTS: Int = 429
    private val SUCCESS_STATUS_RANGE: IntRange = 200..299
    private val HTTP_SERVER_ERROR_RANGE: IntRange = 500..599

    private const val MAX_CLOUD_PERCENT: Int = 100
    private const val MAX_CLOUD_BYTE: Int = 255
    private const val PERCENT_ROUNDING: Int = MAX_CLOUD_PERCENT / 2
    private const val ENCODED_OFFSET: Int = 1
    private const val ALPHA_MASK: Int = 0xFF
    private const val RED_MASK: Int = 0xFF
    private const val GREEN_MASK: Int = 0xFF
    private const val BLUE_MASK: Int = 0xFF
    private const val ALPHA_SHIFT: Int = 24
    private const val RED_SHIFT: Int = 16
    private const val GREEN_SHIFT: Int = 8

    private const val CLOUD_HAZE_CUTOFF: Int = 8
    private const val CLOUD_FEATHER_CENTER_WEIGHT: Int = 4
    private const val CLOUD_FEATHER_MAX_WEIGHT: Int = 1
    private const val CLOUD_FEATHER_WEIGHT: Int =
      CLOUD_FEATHER_CENTER_WEIGHT + CLOUD_FEATHER_MAX_WEIGHT
    private const val CLOUD_FEATHER_ROUNDING: Int = CLOUD_FEATHER_WEIGHT / 2
    private const val CLOUD_EDGE_BOOST_DIVISOR: Int = 8

    private val CLOUD_LAYERS: Array<String> =
      arrayOf(
        "MODIS_Aqua_Cloud_Fraction_Day",
        "MODIS_Terra_Cloud_Fraction_Day",
        "MODIS_Aqua_Cloud_Fraction_Night",
        "MODIS_Terra_Cloud_Fraction_Night"
      )
    private val FACES: Array<String> = arrayOf("px", "nx", "py", "ny", "pz", "nz")
    private val LEGACY_RAW_FACE_VERSIONS: Array<String> =
      arrayOf("-cl-v4", "-shape-v3", "-shape-v2")

    internal fun cloudImageUri(
      date: LocalDate,
      layer: String
    ): URI =
      URI.create(
        "$WMS_BASE_URL?SERVICE=WMS&REQUEST=GetMap&VERSION=1.1.1" +
          "&LAYERS=$layer&STYLES=default&FORMAT=image/png&TRANSPARENT=true" +
          "&SRS=EPSG:4326&BBOX=-180,-90,180,90" +
          "&WIDTH=$SOURCE_WIDTH&HEIGHT=$SOURCE_HEIGHT&TIME=$date"
      )

    internal fun latestCompleteDate(currentUtcDate: LocalDate): LocalDate =
      currentUtcDate.minusDays(1)

    internal fun cloudCacheDateFile(cacheDirectory: File): File =
      File(cacheDirectory, CACHE_DATE_FILE_NAME)

    internal fun isCacheCurrent(
      cacheDirectory: File,
      observationDate: LocalDate
    ): Boolean {
      val cacheDateFile: File = cloudCacheDateFile(cacheDirectory)
      val cachedDate: String =
        try {
          cacheDateFile.readText().trim()
        } catch (_: Exception) {
          return false
        }
      if (cachedDate != observationDate.toString()) {
        return false
      }
      val expectedFaceBytes: Long = FACE.toLong() * FACE
      for (face: String in FACES) {
        val faceFile: File = File(cacheDirectory, face + RAW_FACE_VERSION + RAW_FACE_EXTENSION)
        if (!faceFile.isFile || faceFile.length() != expectedFaceBytes) {
          return false
        }
      }
      return true
    }

    internal fun decodeCloudPercentage(argb: Int): Int? {
      val alpha: Int = (argb ushr ALPHA_SHIFT) and ALPHA_MASK
      if (alpha == 0) {
        return null
      }
      val red: Int = (argb ushr RED_SHIFT) and RED_MASK
      val green: Int = (argb ushr GREEN_SHIFT) and GREEN_MASK
      val blue: Int = argb and BLUE_MASK
      return when {
        red == 102 && green in 0..5 && blue == 119 -> green
        red == 183 && green in 15..20 && blue == 141 -> green - 9
        red == 0 && green in 0..6 && blue == 100 -> green + 12
        red == 0 && green in 0..5 && blue == 170 -> green + 19
        red == 0 && green in 0..5 && blue == 255 -> green + 25
        red in 0..6 && green == 136 && blue == 238 -> red + 31
        red in 0..5 && green == 80 && blue == 0 -> red + 38
        red in 0..5 && green == 136 && blue == 0 -> red + 44
        red in 0..6 && green == 220 && blue == 0 -> red + 50
        red == 255 && green == 255 && blue in 0..5 -> blue + 57
        red == 240 && green == 190 && blue in 64..69 -> blue - 1
        red == 187 && green == 136 && blue in 0..6 -> blue + 69
        red == 122 && green == 90 && blue in 3..8 -> blue + 73
        red == 110 && green == 0 && blue in 0..5 -> blue + 82
        red == 170 && green == 0 && blue in 0..6 -> blue + 88
        red == 255 && green == 0 && blue in 0..5 -> blue + 95
        else -> null
      }
    }

    internal fun mergeCloudRow(
      encodedClouds: ShortArray,
      nasaPixels: IntArray,
      rowOffset: Int
    ): Int {
      require(rowOffset >= 0)
      require(rowOffset + nasaPixels.size <= encodedClouds.size)
      var newlyCovered = 0
      for (column: Int in nasaPixels.indices) {
        val destination: Int = rowOffset + column
        if (encodedClouds[destination].toInt() != 0) {
          continue
        }
        val percentage: Int = decodeCloudPercentage(nasaPixels[column]) ?: continue
        encodedClouds[destination] = encodeCloudPercentage(percentage)
        newlyCovered++
      }
      return newlyCovered
    }

    internal fun fillMissingPixels(
      encodedClouds: ShortArray,
      width: Int,
      height: Int
    ): Boolean {
      require(width > 0)
      require(height > 0)
      require(encodedClouds.size == width * height)

      for (row: Int in 0 until height) {
        val rowStart: Int = row * width
        fillRangeForward(encodedClouds, rowStart, rowStart + width, step = 1)
        fillRangeForward(encodedClouds, rowStart + width - 1, rowStart - 1, step = -1)
      }
      for (column: Int in 0 until width) {
        fillRangeForward(encodedClouds, column, encodedClouds.size, step = width)
        fillRangeForward(
          encodedClouds,
          encodedClouds.size - width + column,
          column - width,
          step = -width
        )
      }
      for (encodedCloud: Short in encodedClouds) {
        if (encodedCloud.toInt() == 0) {
          return false
        }
      }
      return true
    }

    private fun fillRangeForward(
      values: ShortArray,
      start: Int,
      endExclusive: Int,
      step: Int
    ) {
      var lastObserved: Short = 0
      var index: Int = start
      while (
        (step > 0 && index < endExclusive) ||
          (step < 0 && index > endExclusive)
      ) {
        val value: Short = values[index]
        if (value.toInt() == 0) {
          if (lastObserved.toInt() != 0) {
            values[index] = lastObserved
          }
        } else {
          lastObserved = value
        }
        index += step
      }
    }

    internal fun isRetriableStatusCode(statusCode: Int): Boolean =
      statusCode == HTTP_REQUEST_TIMEOUT ||
        statusCode == HTTP_TOO_MANY_REQUESTS ||
        statusCode in HTTP_SERVER_ERROR_RANGE

    internal fun smoothCloudRows(
      previousRow: ByteArray,
      currentRow: ByteArray,
      nextRow: ByteArray,
      outputRow: ByteArray
    ) {
      require(previousRow.size == currentRow.size)
      require(nextRow.size == currentRow.size)
      require(outputRow.size == currentRow.size)

      for (x: Int in outputRow.indices) {
        val centerCloud: Int = getCloudRowValue(currentRow, x)
        val maxCloud: Int = getMaxCloudRowValue(previousRow, currentRow, nextRow, x)
        val minCloud: Int = getMinCloudRowValue(previousRow, currentRow, nextRow, x)
        outputRow[x] = shapeCloudValue(centerCloud, maxCloud, minCloud).toByte()
      }
    }

    private fun encodeCloudPercentage(percentage: Int): Short =
      (
          (percentage * MAX_CLOUD_BYTE + PERCENT_ROUNDING) / MAX_CLOUD_PERCENT +
            ENCODED_OFFSET
        ).toShort()

    private fun hasCoverage(
      coveredPixels: Int,
      requiredPermille: Int
    ): Boolean =
      coveredPixels.toLong() * COVERAGE_SCALE >=
        SOURCE_PIXEL_COUNT.toLong() * requiredPermille

    private fun coveragePercent(coveredPixels: Int): Float =
      coveredPixels.toFloat() * MAX_CLOUD_PERCENT / SOURCE_PIXEL_COUNT

    private fun deleteLegacyFaces(cacheDirectory: File) {
      for (face: String in FACES) {
        for (version: String in LEGACY_RAW_FACE_VERSIONS) {
          File(cacheDirectory, face + version + RAW_FACE_EXTENSION).delete()
        }
      }
    }

    private fun writeCacheDate(
      cacheDirectory: File,
      observationDate: LocalDate
    ) {
      try {
        cloudCacheDateFile(cacheDirectory).writeText(observationDate.toString())
      } catch (e: Exception) {
        Log.w(TAG, "Failed writing NASA cache date", e)
      }
    }

    private fun wrapHorizontal(
      sourceX: Double,
      width: Int
    ): Double {
      val widthDouble: Double = width.toDouble()
      return ((sourceX % widthDouble) + widthDouble) % widthDouble
    }

    private fun getCloudRowValue(
      row: ByteArray,
      x: Int
    ): Int {
      val clampedX: Int = x.coerceIn(0, row.size - 1)
      return row[clampedX].toInt() and MAX_CLOUD_BYTE
    }

    private fun getMaxCloudRowValue(
      previousRow: ByteArray,
      currentRow: ByteArray,
      nextRow: ByteArray,
      x: Int
    ): Int {
      var maxCloud: Int = getCloudRowValue(currentRow, x)
      maxCloud = max(maxCloud, getCloudRowValue(previousRow, x - 1))
      maxCloud = max(maxCloud, getCloudRowValue(previousRow, x))
      maxCloud = max(maxCloud, getCloudRowValue(previousRow, x + 1))
      maxCloud = max(maxCloud, getCloudRowValue(currentRow, x - 1))
      maxCloud = max(maxCloud, getCloudRowValue(currentRow, x + 1))
      maxCloud = max(maxCloud, getCloudRowValue(nextRow, x - 1))
      maxCloud = max(maxCloud, getCloudRowValue(nextRow, x))
      maxCloud = max(maxCloud, getCloudRowValue(nextRow, x + 1))
      return maxCloud
    }

    private fun getMinCloudRowValue(
      previousRow: ByteArray,
      currentRow: ByteArray,
      nextRow: ByteArray,
      x: Int
    ): Int {
      var minCloud: Int = getCloudRowValue(currentRow, x)
      minCloud = min(minCloud, getCloudRowValue(previousRow, x - 1))
      minCloud = min(minCloud, getCloudRowValue(previousRow, x))
      minCloud = min(minCloud, getCloudRowValue(previousRow, x + 1))
      minCloud = min(minCloud, getCloudRowValue(currentRow, x - 1))
      minCloud = min(minCloud, getCloudRowValue(currentRow, x + 1))
      minCloud = min(minCloud, getCloudRowValue(nextRow, x - 1))
      minCloud = min(minCloud, getCloudRowValue(nextRow, x))
      minCloud = min(minCloud, getCloudRowValue(nextRow, x + 1))
      return minCloud
    }

    private fun shapeCloudValue(
      centerCloud: Int,
      maxCloud: Int,
      minCloud: Int
    ): Int {
      if (maxCloud <= CLOUD_HAZE_CUTOFF) {
        return 0
      }
      val featheredCloud: Int =
        (
            centerCloud * CLOUD_FEATHER_CENTER_WEIGHT +
              maxCloud * CLOUD_FEATHER_MAX_WEIGHT +
              CLOUD_FEATHER_ROUNDING
          ) / CLOUD_FEATHER_WEIGHT
      val edgeBoost: Int = (maxCloud - minCloud) / CLOUD_EDGE_BOOST_DIVISOR
      val clippedCloud: Int =
        (featheredCloud + edgeBoost - CLOUD_HAZE_CUTOFF).coerceAtLeast(0)
      return (clippedCloud * MAX_CLOUD_BYTE / (MAX_CLOUD_BYTE - CLOUD_HAZE_CUTOFF))
        .coerceIn(0, MAX_CLOUD_BYTE)
    }

    private fun lerp(
      start: Double,
      end: Double,
      amount: Double
    ): Double = start + (end - start) * amount
  }
}

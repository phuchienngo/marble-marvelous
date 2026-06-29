package com.phuchienngo.marblemarvelous.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.phuchienngo.marblemarvelous.di.DefaultDispatcher
import com.phuchienngo.marblemarvelous.utils.Console
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.coroutines.executeAsync
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.LinkedHashMap
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Builds the 6 cube-map faces (px/nx/.../nz.r8) consumed by
 * [CloudsProvider.getLatest] from OpenWeatherMap cloud map tiles.
 *
 * Replaces the dead Google clouds-cubemap download. OpenWeather only serves 2D
 * web-mercator tiles, so the global cloud layer is re-projected onto an OpenGL
 * cube map (no GL/shader changes needed). Set the key in strings.xml ->
 * openweather_api_key. Empty key keeps the bundled earth/clouds.ktx.
 *
 * NOTE: cube orientation vs the Earth model may need a [LON_OFFSET_DEG] tweak
 * once it can be run on a device.
 */
class OpenWeatherClouds
    @Inject
    constructor(
        private val httpClient: OkHttpClient,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher
    ) {
        suspend fun generateCubeFaces(
            context: Context,
            apiKey: String?
        ): Boolean {
            if (apiKey.isNullOrBlank()) {
                Console.info(TAG, "No OpenWeather API key set. Keeping bundled clouds.")
                return false
            }
            return withContext(defaultDispatcher) generateFaces@{
                val tileDirectory: File = File(context.cacheDir, TILE_CACHE_DIRECTORY)
                if (!resetTileDirectory(tileDirectory)) {
                    return@generateFaces false
                }
                try {
                    val source: TiledCloudSource =
                        TiledCloudSource(
                            tileDirectory = tileDirectory,
                            tilesPerAxis = 1 shl SRC_ZOOM,
                            tileSize = TILE,
                            maxCachedTiles = TILE_CACHE_SIZE,
                            tileLoader =
                                TileLoader loadCloudTile@{ x: Int, y: Int ->
                                    return@loadCloudTile downloadCloudTileValues(apiKey, x, y)
                                }
                        )
                    for (faceIndex: Int in FACES.indices) {
                        val ok: Boolean =
                            writeRawFace(
                                faceIndex = faceIndex,
                                source = source,
                                dest = File(context.cacheDir, FACES[faceIndex] + RAW_FACE_VERSION + RAW_FACE_EXTENSION)
                            )
                        if (!ok) {
                            return@generateFaces false
                        }
                    }
                } finally {
                    tileDirectory.deleteRecursively()
                }
                return@generateFaces true
            }
        }

        /**
         * Downloads one mercator tile on demand and converts it to raw grayscale tile
         * data. [TiledCloudSource] persists successful loads so later samples reuse
         * disk/cache instead of hitting the network again.
         */
        private suspend fun downloadCloudTileValues(
            apiKey: String,
            x: Int,
            y: Int
        ): ByteArray? {
            val tilePixels: IntArray = IntArray(TILE * TILE)
            val tileValues: ByteArray = ByteArray(TILE * TILE)
            val tile: Bitmap = downloadTile(SRC_ZOOM, x, y, apiKey) ?: return null
            try {
                tile.getPixels(tilePixels, 0, TILE, 0, 0, TILE, TILE)
                for (pixelIndex in tilePixels.indices) {
                    tileValues[pixelIndex] = getCloudValue(tilePixels[pixelIndex]).toByte()
                }
                return tileValues
            } finally {
                tile.recycle()
            }
        }

        private suspend fun downloadTile(
            z: Int,
            x: Int,
            y: Int,
            apiKey: String
        ): Bitmap? {
            val url: String = "https://tile.openweathermap.org/map/clouds_new/$z/$x/$y.png?appid=$apiKey"
            val request: Request =
                Request
                    .Builder()
                    .url(url)
                    .build()
            // A whole-world z=5 fetch is ~1024 tiles; a single transient failure (429,
            // timeout) must not abort the batch, so retry with a short backoff.
            for (attempt in 0 until TILE_ATTEMPTS) {
                try {
                    httpClient
                        .newCall(request)
                        .executeAsync()
                        .use downloadResponse@{ response: Response ->
                            if (!response.isSuccessful) {
                                Console.warn(TAG, "Tile $z/$x/$y -> HTTP ${response.code} (attempt ${attempt + 1})")
                                return@downloadResponse
                            }
                            val bitmap: Bitmap? =
                                response.body
                                    ?.byteStream()
                                    ?.use decodeTile@{ stream: InputStream ->
                                        return@decodeTile BitmapFactory.decodeStream(stream)
                                    }
                            if (bitmap != null) {
                                return bitmap
                            }
                            return@downloadResponse
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Tile $z/$x/$y download failed (attempt ${attempt + 1})", e)
                }
                if (attempt < TILE_ATTEMPTS - 1) {
                    delay(TILE_RETRY_DELAY_MS)
                }
            }
            return null
        }

        private suspend fun writeRawFace(
            faceIndex: Int,
            source: TiledCloudSource,
            dest: File
        ): Boolean {
            var outputStream: FileOutputStream? = null
            return try {
                outputStream = FileOutputStream(dest)
                val ok: Boolean = writeSmoothedRawFace(faceIndex, source, outputStream)
                if (!ok) {
                    dest.delete()
                }
                ok
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing $dest", e)
                dest.delete()
                false
            } finally {
                try {
                    outputStream?.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed closing $dest", e)
                }
            }
        }

        private suspend fun writeSmoothedRawFace(
            faceIndex: Int,
            source: TiledCloudSource,
            outputStream: FileOutputStream
        ): Boolean {
            var previousRow: ByteArray? = null
            var currentRow: ByteArray = sampleCloudRow(faceIndex, py = 0, source) ?: return false
            var nextRow: ByteArray? =
                if (FACE > 1) {
                    sampleCloudRow(faceIndex, py = 1, source) ?: return false
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
                        sampleCloudRow(faceIndex, nextY, source) ?: return false
                    } else {
                        null
                    }
            }
            return true
        }

        private suspend fun sampleCloudRow(
            faceIndex: Int,
            py: Int,
            source: TiledCloudSource
        ): ByteArray? {
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
                val len: Double = sqrt(dx * dx + dy * dy + dz * dz)
                dx /= len
                dy /= len
                dz /= len
                val latDeg: Double = Math.toDegrees(asin(max(-1.0, min(1.0, dy))))
                var lonDeg = Math.toDegrees(atan2(dx, dz)) + LON_OFFSET_DEG
                lonDeg = ((lonDeg + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
                val sampledCloud: Int = sampleMercator(source, latDeg, lonDeg) ?: return null
                row[px] = sampledCloud.toByte()
            }
            return row
        }

        private suspend fun sampleMercator(
            source: TiledCloudSource,
            latDeg: Double,
            lonDeg: Double
        ): Int? {
            val clampedLat: Double = max(-MERC_LAT_LIMIT, min(MERC_LAT_LIMIT, latDeg))
            val latRad: Double = Math.toRadians(clampedLat)
            val xf: Double = (lonDeg + 180.0) / 360.0
            val yf: Double = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0
            val sourceX: Double = xf * source.worldSize - 0.5
            val sourceY: Double = yf * source.worldSize - 0.5
            return source.sample(sourceX, sourceY)
        }

        internal fun interface TileLoader {
            suspend fun loadTile(
                x: Int,
                y: Int
            ): ByteArray?
        }

        internal class TiledCloudSource(
            private val tileDirectory: File,
            private val tilesPerAxis: Int,
            private val tileSize: Int,
            maxCachedTiles: Int,
            private val tileLoader: TileLoader?
        ) {
            val worldSize: Int = tilesPerAxis * tileSize
            private val cache: LinkedHashMap<Long, ByteArray> =
                object : LinkedHashMap<Long, ByteArray>(maxCachedTiles, LOAD_FACTOR, true) {
                    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, ByteArray>?): Boolean = size > maxCachedTiles
                }

            init {
                require(tilesPerAxis > 0)
                require(tileSize > 0)
                require(maxCachedTiles > 0)
            }

            suspend fun sample(
                sourceX: Double,
                sourceY: Double
            ): Int? {
                val wrappedX: Double = wrapHorizontal(sourceX, worldSize)
                val clampedY: Double = sourceY.coerceIn(0.0, (worldSize - 1).toDouble())
                val x0: Int = floor(wrappedX).toInt()
                val y0: Int = floor(clampedY).toInt()
                val x1: Int = (x0 + 1) % worldSize
                val y1: Int = minOf(y0 + 1, worldSize - 1)
                val xWeight: Double = wrappedX - x0
                val yWeight: Double = clampedY - y0

                val topLeft: Int = getCloudValueAt(x0, y0) ?: return null
                val topRight: Int = getCloudValueAt(x1, y0) ?: return null
                val bottomLeft: Int = getCloudValueAt(x0, y1) ?: return null
                val bottomRight: Int = getCloudValueAt(x1, y1) ?: return null
                val top: Double = lerp(topLeft.toDouble(), topRight.toDouble(), xWeight)
                val bottom: Double = lerp(bottomLeft.toDouble(), bottomRight.toDouble(), xWeight)
                return lerp(top, bottom, yWeight)
                    .roundToInt()
                    .coerceIn(0, 255)
            }

            fun cachedTileCount(): Int = cache.size

            private suspend fun getCloudValueAt(
                x: Int,
                y: Int
            ): Int? {
                val tileX: Int = x / tileSize
                val tileY: Int = y / tileSize
                val localX: Int = x % tileSize
                val localY: Int = y % tileSize
                val tile: ByteArray = getTile(tileX, tileY) ?: return null
                return tile[localY * tileSize + localX].toInt() and 0xFF
            }

            private suspend fun getTile(
                x: Int,
                y: Int
            ): ByteArray? {
                val key: Long = tileKey(x, y)
                val cached: ByteArray? = cache[key]
                if (cached != null) {
                    return cached
                }
                val loaded: ByteArray = loadTile(x, y) ?: return null
                if (loaded.size < tileSize * tileSize) {
                    Log.e(TAG, "Cloud tile $x/$y has ${loaded.size} bytes, expected ${tileSize * tileSize}")
                    return null
                }
                cache[key] = loaded
                return loaded
            }

            private suspend fun loadTile(
                x: Int,
                y: Int
            ): ByteArray? {
                val file: File = tileFile(tileDirectory, x, y)
                if (file.exists()) {
                    return file.readBytes()
                }
                val loaded: ByteArray = tileLoader?.loadTile(x, y) ?: return null
                return if (writeTile(file, loaded)) {
                    loaded
                } else {
                    null
                }
            }

            private fun writeTile(
                file: File,
                values: ByteArray
            ): Boolean {
                return try {
                    FileOutputStream(file).use writeTile@{ outputStream: FileOutputStream ->
                        outputStream.write(values)
                        return@writeTile
                    }
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed writing cloud tile ${file.name}", e)
                    false
                }
            }

            private fun tileKey(
                x: Int,
                y: Int
            ): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
        }

        companion object {
            private const val TAG: String = "OWClouds"

            /**
             * Mercator zoom for the source tiles. z=3 -> 8x8 = 64 tiles = 2048px
             * whole world, giving ~512px per equatorial cube face = native [FACE] detail.
             * Tile bytes are cached on disk and read through [TiledCloudSource], avoiding
             * a full 2048² source buffer in Java heap.
             */
            private const val SRC_ZOOM: Int = 3
            private const val TILE: Int = 256

            // Cube face size. 512 keeps the OpenWeather layer light; shader sampling softens
            // the lower-resolution cloud map into a puffier layer.
            private const val FACE: Int = 512
            private const val TILE_CACHE_SIZE: Int = 32
            private const val TILE_CACHE_DIRECTORY: String = "openweather_cloud_tiles"
            private const val MERC_LAT_LIMIT: Double = 85.05112878
            private const val LON_OFFSET_DEG: Double = 0.0
            private const val RAW_FACE_VERSION: String = "-shape-v2"
            private const val RAW_FACE_EXTENSION: String = ".r8"
            private const val TILE_ATTEMPTS: Int = 3
            private const val TILE_RETRY_DELAY_MS: Long = 1500L

            // Center-heavy 3x3 kernel (1-2-1 / 2-8-2 / 1-2-1) = light denoise that
            // keeps cloud detail, instead of the old even 2-4-2 Gaussian which blurred
            // the real-time clouds noticeably softer than the 2048² earth map.
            private const val SMOOTH_KERNEL_ROUNDING: Int = 10
            private const val SMOOTH_KERNEL_WEIGHT: Int = 20
            private const val CLOUD_HAZE_CUTOFF: Int = 8
            private const val CLOUD_FEATHER_BLUR_WEIGHT: Int = 3
            private const val CLOUD_FEATHER_MAX_WEIGHT: Int = 2
            private const val CLOUD_FEATHER_WEIGHT: Int = CLOUD_FEATHER_BLUR_WEIGHT + CLOUD_FEATHER_MAX_WEIGHT
            private const val CLOUD_FEATHER_ROUNDING: Int = CLOUD_FEATHER_WEIGHT / 2
            private const val CLOUD_EDGE_BOOST_DIVISOR: Int = 8
            private const val LOAD_FACTOR: Float = 0.75f

            private val FACES: Array<String> = arrayOf("px", "nx", "py", "ny", "pz", "nz")

            internal fun tileFile(
                directory: File,
                x: Int,
                y: Int
            ): File = File(directory, "$x-$y.raw")

            private fun resetTileDirectory(directory: File): Boolean {
                if (directory.exists() && !directory.deleteRecursively()) {
                    return false
                }
                return directory.mkdirs() || directory.isDirectory
            }

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
                    val blurredCloud: Int = getSmoothedCloudRowValue(previousRow, currentRow, nextRow, x)
                    val maxCloud: Int = getMaxCloudRowValue(previousRow, currentRow, nextRow, x)
                    val minCloud: Int = getMinCloudRowValue(previousRow, currentRow, nextRow, x)
                    val cloud: Int = shapeCloudValue(blurredCloud, maxCloud, minCloud)
                    outputRow[x] = cloud.toByte()
                }
            }

            private fun wrapHorizontal(
                sourceX: Double,
                width: Int
            ): Double {
                val widthDouble: Double = width.toDouble()
                return ((sourceX % widthDouble) + widthDouble) % widthDouble
            }

            internal fun getCloudValue(argb: Int): Int {
                val alpha: Int = (argb ushr 24) and 0xFF
                val luma: Int =
                    (
                        0.299 * ((argb shr 16) and 0xFF) +
                            0.587 * ((argb shr 8) and 0xFF) +
                            0.114 * (argb and 0xFF)
                    ).toInt()
                return (alpha * luma) / 255
            }

            private fun getCloudRowValue(
                row: ByteArray,
                x: Int
            ): Int {
                val clampedX: Int = x.coerceIn(0, row.size - 1)
                return row[clampedX].toInt() and 0xFF
            }

            private fun getSmoothedCloudRowValue(
                previousRow: ByteArray,
                currentRow: ByteArray,
                nextRow: ByteArray,
                x: Int
            ): Int =
                (
                    getCloudRowValue(previousRow, x - 1) +
                        getCloudRowValue(previousRow, x) * 2 +
                        getCloudRowValue(previousRow, x + 1) +
                        getCloudRowValue(currentRow, x - 1) * 2 +
                        getCloudRowValue(currentRow, x) * 8 +
                        getCloudRowValue(currentRow, x + 1) * 2 +
                        getCloudRowValue(nextRow, x - 1) +
                        getCloudRowValue(nextRow, x) * 2 +
                        getCloudRowValue(nextRow, x + 1) +
                        SMOOTH_KERNEL_ROUNDING
                ) / SMOOTH_KERNEL_WEIGHT

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
                blurredCloud: Int,
                maxCloud: Int,
                minCloud: Int
            ): Int {
                if (maxCloud <= CLOUD_HAZE_CUTOFF) {
                    return 0
                }

                val featheredCloud: Int =
                    (
                        blurredCloud * CLOUD_FEATHER_BLUR_WEIGHT +
                            maxCloud * CLOUD_FEATHER_MAX_WEIGHT +
                            CLOUD_FEATHER_ROUNDING
                    ) / CLOUD_FEATHER_WEIGHT
                val edgeBoost: Int = (maxCloud - minCloud) / CLOUD_EDGE_BOOST_DIVISOR
                val liftedCloud: Int = featheredCloud + edgeBoost
                val clippedCloud: Int = (liftedCloud - CLOUD_HAZE_CUTOFF).coerceAtLeast(0)
                return (clippedCloud * 255 / (255 - CLOUD_HAZE_CUTOFF)).coerceIn(0, 255)
            }

            private fun lerp(
                start: Double,
                end: Double,
                amount: Double
            ): Double = start + (end - start) * amount

        }
    }

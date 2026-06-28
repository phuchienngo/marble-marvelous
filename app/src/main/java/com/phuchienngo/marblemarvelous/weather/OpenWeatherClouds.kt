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
 * Builds the 6 cube-map faces (px/nx/.../nz.jpg) consumed by
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
                    for (faceIndex in FACES.indices) {
                        val face: Bitmap = renderFace(faceIndex, source) ?: return@generateFaces false
                        val ok: Boolean = writePng(face, File(context.cacheDir, FACES[faceIndex] + FACE_EXTENSION))
                        face.recycle()
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

        private suspend fun renderFace(
            faceIndex: Int,
            source: TiledCloudSource
        ): Bitmap? {
            val face: Bitmap = Bitmap.createBitmap(FACE, FACE, Bitmap.Config.ARGB_8888)
            val row: IntArray = IntArray(FACE)
            for (py in 0 until FACE) {
                val t: Double = 2.0 * (py + 0.5) / FACE - 1.0
                for (px in 0 until FACE) {
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
                    val sampledCloud: Int? = sampleMercator(source, latDeg, lonDeg)
                    if (sampledCloud == null) {
                        face.recycle()
                        return null
                    }
                    val cloud: Int = sampledCloud
                    row[px] = toCloudArgb(cloud)
                }
                face.setPixels(row, 0, FACE, 0, py, FACE, 1)
            }
            val facePixels: IntArray = IntArray(FACE * FACE)
            face.getPixels(facePixels, 0, FACE, 0, 0, FACE, FACE)
            face.setPixels(smoothCloudFace(facePixels, FACE, FACE), 0, FACE, 0, 0, FACE, FACE)
            return face
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

        private fun writePng(
            bitmap: Bitmap,
            dest: File
        ): Boolean {
            return try {
                FileOutputStream(dest).use writeBitmap@{ fos: FileOutputStream ->
                    return@writeBitmap bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, fos)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed writing $dest", e)
                false
            }
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
             * Mercator zoom for the source tiles. z=5 -> 32x32 = 1024 tiles = 8192px
             * whole world, giving ~2048px per equatorial cube face = native [FACE] detail.
             * Tile bytes are cached on disk and read through [TiledCloudSource], avoiding
             * a full 8192² source buffer in Java heap.
             */
            private const val SRC_ZOOM: Int = 5
            private const val TILE: Int = 256

            // Cube face size. 2048 matches the day/night earth maps (2048²/face) so the
            // cloud cubemap renders without an extra GPU upscale, and at z=5 the source
            // actually carries ~2048px/equatorial-face so this is near-native detail.
            private const val FACE: Int = 2048
            private const val TILE_CACHE_SIZE: Int = 32
            private const val TILE_CACHE_DIRECTORY: String = "openweather_cloud_tiles"
            private const val MERC_LAT_LIMIT: Double = 85.05112878
            private const val LON_OFFSET_DEG: Double = 0.0
            private const val PNG_QUALITY: Int = 100
            private const val FACE_EXTENSION: String = ".png"
            private const val TILE_ATTEMPTS: Int = 3
            private const val TILE_RETRY_DELAY_MS: Long = 1500L

            // Center-heavy 3x3 kernel (1-2-1 / 2-8-2 / 1-2-1) = light denoise that
            // keeps cloud detail, instead of the old even 2-4-2 Gaussian which blurred
            // the real-time clouds noticeably softer than the 2048² earth map.
            private const val SMOOTH_KERNEL_ROUNDING: Int = 10
            private const val SMOOTH_KERNEL_WEIGHT: Int = 20
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

            internal fun smoothCloudFace(
                pixels: IntArray,
                width: Int,
                height: Int
            ): IntArray {
                require(width > 0)
                require(height > 0)
                require(pixels.size == width * height)

                val smoothed: IntArray = IntArray(pixels.size)
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        val cloud: Int =
                            (
                                getCloudFaceValue(pixels, width, height, x - 1, y - 1) +
                                    getCloudFaceValue(pixels, width, height, x, y - 1) * 2 +
                                    getCloudFaceValue(pixels, width, height, x + 1, y - 1) +
                                    getCloudFaceValue(pixels, width, height, x - 1, y) * 2 +
                                    getCloudFaceValue(pixels, width, height, x, y) * 8 +
                                    getCloudFaceValue(pixels, width, height, x + 1, y) * 2 +
                                    getCloudFaceValue(pixels, width, height, x - 1, y + 1) +
                                    getCloudFaceValue(pixels, width, height, x, y + 1) * 2 +
                                    getCloudFaceValue(pixels, width, height, x + 1, y + 1) +
                                    SMOOTH_KERNEL_ROUNDING
                            ) / SMOOTH_KERNEL_WEIGHT
                        smoothed[y * width + x] = toCloudArgb(cloud)
                    }
                }
                return smoothed
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
                return if (alpha == 0) {
                    luma
                } else {
                    (alpha * luma) / 255
                }
            }

            private fun getCloudFaceValue(
                pixels: IntArray,
                width: Int,
                height: Int,
                x: Int,
                y: Int
            ): Int {
                val clampedX: Int = x.coerceIn(0, width - 1)
                val clampedY: Int = y.coerceIn(0, height - 1)
                return pixels[clampedY * width + clampedX] and 0xFF
            }

            private fun lerp(
                start: Double,
                end: Double,
                amount: Double
            ): Double = start + (end - start) * amount

            private fun toCloudArgb(cloud: Int): Int = -0x1000000 or (cloud shl 16) or (cloud shl 8) or cloud
        }
    }

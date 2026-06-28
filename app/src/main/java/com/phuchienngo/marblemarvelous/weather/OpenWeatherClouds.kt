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
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) {
        suspend fun generateCubeFaces(
            context: Context,
            apiKey: String?,
        ): Boolean {
            if (apiKey.isNullOrBlank()) {
                Console.info(TAG, "No OpenWeather API key set. Keeping bundled clouds.")
                return false
            }
            return withContext(defaultDispatcher) generateFaces@{
                // The world cloud layer is stored as ONE byte per pixel (cloud 0..255),
                // not an ARGB bitmap. That is 4x smaller, so the source can be z=5
                // (8192² -> 64MB ByteArray) instead of being capped at z=4 by a ~512MB
                // ARGB bitmap. Tiles are streamed in and discarded immediately.
                val field: ByteArray = downloadCloudField(apiKey) ?: return@generateFaces false
                for (faceIndex in FACES.indices) {
                    val face: Bitmap = renderFace(faceIndex, field, SRC, SRC)
                    val ok: Boolean = writePng(face, File(context.cacheDir, FACES[faceIndex] + FACE_EXTENSION))
                    face.recycle()
                    if (!ok) {
                        return@generateFaces false
                    }
                }
                return@generateFaces true
            }
        }

        /**
         * Downloads every mercator tile at [SRC_ZOOM] and folds it into a single
         * grayscale cloud field of [SRC]×[SRC] bytes. Each tile bitmap is decoded,
         * reduced to cloud values and recycled before the next, so peak memory is the
         * field plus one tile — not the whole world as ARGB.
         */
        private suspend fun downloadCloudField(apiKey: String): ByteArray? {
            val tiles: Int = 1 shl SRC_ZOOM
            val field = ByteArray(SRC * SRC)
            val tilePixels = IntArray(TILE * TILE)
            for (tx in 0 until tiles) {
                for (ty in 0 until tiles) {
                    val tile: Bitmap = downloadTile(SRC_ZOOM, tx, ty, apiKey) ?: return null
                    try {
                        tile.getPixels(tilePixels, 0, TILE, 0, 0, TILE, TILE)
                        val baseX: Int = tx * TILE
                        val baseY: Int = ty * TILE
                        for (py in 0 until TILE) {
                            val destRow: Int = (baseY + py) * SRC + baseX
                            val srcRow: Int = py * TILE
                            for (px in 0 until TILE) {
                                field[destRow + px] = getCloudValue(tilePixels[srcRow + px]).toByte()
                            }
                        }
                    } finally {
                        tile.recycle()
                    }
                }
            }
            return field
        }

        private suspend fun downloadTile(
            z: Int,
            x: Int,
            y: Int,
            apiKey: String,
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

        private fun renderFace(
            faceIndex: Int,
            field: ByteArray,
            srcW: Int,
            srcH: Int,
        ): Bitmap {
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
                    row[px] = toCloudArgb(sampleMercator(field, srcW, srcH, latDeg, lonDeg))
                }
                face.setPixels(row, 0, FACE, 0, py, FACE, 1)
            }
            val facePixels: IntArray = IntArray(FACE * FACE)
            face.getPixels(facePixels, 0, FACE, 0, 0, FACE, FACE)
            face.setPixels(smoothCloudFace(facePixels, FACE, FACE), 0, FACE, 0, 0, FACE, FACE)
            return face
        }

        private fun sampleMercator(
            field: ByteArray,
            w: Int,
            h: Int,
            latDeg: Double,
            lonDeg: Double,
        ): Int {
            val clampedLat: Double = max(-MERC_LAT_LIMIT, min(MERC_LAT_LIMIT, latDeg))
            val latRad: Double = Math.toRadians(clampedLat)
            val xf: Double = (lonDeg + 180.0) / 360.0
            val yf: Double = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0
            val sourceX: Double = xf * w - 0.5
            val sourceY: Double = yf * h - 0.5
            return sampleBilinearCloud(field, w, h, sourceX, sourceY)
        }

        private fun writePng(
            bitmap: Bitmap,
            dest: File,
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

        companion object {
            private const val TAG: String = "OWClouds"

            /**
             * Mercator zoom for the source tiles. z=5 -> 32x32 = 1024 tiles = 8192px
             * whole world, giving ~2048px per equatorial cube face = native [FACE] detail.
             * Affordable only because the world is held as a 1-byte/pixel field (64MB),
             * not an ARGB bitmap (256MB); see [downloadCloudField].
             */
            private const val SRC_ZOOM: Int = 5
            private const val TILE: Int = 256
            private const val SRC: Int = TILE shl SRC_ZOOM
            // Cube face size. 2048 matches the day/night earth maps (2048²/face) so the
            // cloud cubemap renders without an extra GPU upscale, and at z=5 the source
            // actually carries ~2048px/equatorial-face so this is near-native detail.
            private const val FACE: Int = 2048
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

            private val FACES: Array<String> = arrayOf("px", "nx", "py", "ny", "pz", "nz")

            /**
             * Bilinearly samples the grayscale cloud [field] (one byte per pixel, 0..255)
             * with horizontal wrap and vertical clamp. Returns a cloud value 0..255 (the
             * caller packs it to ARGB); the field already holds cloud bytes so no per-tap
             * luma/alpha reduction is needed here.
             */
            internal fun sampleBilinearCloud(
                field: ByteArray,
                width: Int,
                height: Int,
                sourceX: Double,
                sourceY: Double,
            ): Int {
                require(width > 0)
                require(height > 0)
                require(field.size >= width * height)

                val wrappedX: Double = wrapHorizontal(sourceX, width)
                val clampedY: Double = sourceY.coerceIn(0.0, (height - 1).toDouble())
                val x0: Int = floor(wrappedX).toInt()
                val y0: Int = floor(clampedY).toInt()
                val x1: Int = (x0 + 1) % width
                val y1: Int = minOf(y0 + 1, height - 1)
                val xWeight: Double = wrappedX - x0
                val yWeight: Double = clampedY - y0

                val topLeft: Int = field[y0 * width + x0].toInt() and 0xFF
                val topRight: Int = field[y0 * width + x1].toInt() and 0xFF
                val bottomLeft: Int = field[y1 * width + x0].toInt() and 0xFF
                val bottomRight: Int = field[y1 * width + x1].toInt() and 0xFF
                val top: Double = lerp(topLeft.toDouble(), topRight.toDouble(), xWeight)
                val bottom: Double = lerp(bottomLeft.toDouble(), bottomRight.toDouble(), xWeight)
                return lerp(top, bottom, yWeight)
                    .roundToInt()
                    .coerceIn(0, 255)
            }

            internal fun smoothCloudFace(
                pixels: IntArray,
                width: Int,
                height: Int,
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
                width: Int,
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
                y: Int,
            ): Int {
                val clampedX: Int = x.coerceIn(0, width - 1)
                val clampedY: Int = y.coerceIn(0, height - 1)
                return pixels[clampedY * width + clampedX] and 0xFF
            }

            private fun lerp(
                start: Double,
                end: Double,
                amount: Double,
            ): Double = start + (end - start) * amount

            private fun toCloudArgb(cloud: Int): Int = -0x1000000 or (cloud shl 16) or (cloud shl 8) or cloud
        }
    }

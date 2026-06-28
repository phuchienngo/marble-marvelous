package com.phuchienngo.marblemarvelous.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Log
import com.phuchienngo.marblemarvelous.di.DefaultDispatcher
import com.phuchienngo.marblemarvelous.utils.Console
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
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
                val source: Bitmap = downloadMercatorSource(apiKey) ?: return@generateFaces false
                val sourcePixels: IntArray = IntArray(source.width * source.height)
                source.getPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
                var generated: Boolean = false
                try {
                    for (faceIndex in FACES.indices) {
                        val face: Bitmap = renderFace(faceIndex, sourcePixels, source.width, source.height)
                        val ok: Boolean = writePng(face, File(context.cacheDir, FACES[faceIndex] + FACE_EXTENSION))
                        face.recycle()
                        if (!ok) {
                            return@generateFaces false
                        }
                    }
                    generated = true
                } finally {
                    source.recycle()
                }
                return@generateFaces generated
            }
        }

        private suspend fun downloadMercatorSource(apiKey: String): Bitmap? {
            val tiles: Int = 1 shl SRC_ZOOM
            val out: Bitmap = Bitmap.createBitmap(SRC, SRC, Bitmap.Config.ARGB_8888)
            val canvas: Canvas = Canvas(out)
            for (tx in 0 until tiles) {
                for (ty in 0 until tiles) {
                    val tile: Bitmap? = downloadTile(SRC_ZOOM, tx, ty, apiKey)
                    if (tile == null) {
                        out.recycle()
                        return null
                    }
                    canvas.drawBitmap(tile, (tx * TILE).toFloat(), (ty * TILE).toFloat(), null)
                    tile.recycle()
                }
            }
            return out
        }

        private suspend fun downloadTile(
            z: Int,
            x: Int,
            y: Int,
            apiKey: String,
        ): Bitmap? {
            val url: String = "https://tile.openweathermap.org/map/clouds_new/$z/$x/$y.png?appid=$apiKey"
            return try {
                val request: Request =
                    Request
                        .Builder()
                        .url(url)
                        .build()
                httpClient
                    .newCall(request)
                    .executeAsync()
                    .use downloadResponse@{ response: Response ->
                        if (!response.isSuccessful) {
                            Console.warn(TAG, "Tile $z/$x/$y -> HTTP " + response.code)
                            return@downloadResponse null
                        }

                        return@downloadResponse response.body
                            ?.byteStream()
                            ?.use decodeTile@{ stream: InputStream ->
                                return@decodeTile BitmapFactory.decodeStream(stream)
                            }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Tile download failed", e)
                null
            }
        }

        private fun renderFace(
            faceIndex: Int,
            sourcePixels: IntArray,
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
                    row[px] = sampleMercator(sourcePixels, srcW, srcH, latDeg, lonDeg)
                }
                face.setPixels(row, 0, FACE, 0, py, FACE, 1)
            }
            return face
        }

        private fun sampleMercator(
            sourcePixels: IntArray,
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
            return CloudColorSampler.sampleBilinear(sourcePixels, w, h, sourceX, sourceY)
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
             * Mercator zoom for the source tiles. z=4 -> 16x16 tiles = 4096px whole world.
             */
            private const val SRC_ZOOM: Int = 4
            private const val TILE: Int = 256
            private const val SRC: Int = TILE shl SRC_ZOOM
            private const val FACE: Int = 1024
            private const val MERC_LAT_LIMIT: Double = 85.05112878
            private const val LON_OFFSET_DEG: Double = 0.0
            private const val PNG_QUALITY: Int = 100
            private const val FACE_EXTENSION: String = ".png"

            private val FACES: Array<String> = arrayOf("px", "nx", "py", "ny", "pz", "nz")
        }
    }

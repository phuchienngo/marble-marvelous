package com.phuchienngo.marblemarvelous.weather

import android.content.Context
import android.util.Log
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.CubemapData
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.TextureData
import com.badlogic.gdx.graphics.glutils.FacedCubemapData
import com.badlogic.gdx.graphics.glutils.KTXTextureData
import com.badlogic.gdx.graphics.glutils.PixmapTextureData
import com.badlogic.gdx.utils.GdxRuntimeException
import com.phuchienngo.marblemarvelous.di.MainDispatcher
import com.phuchienngo.marblemarvelous.di.OpenWeatherApiKey
import com.phuchienngo.marblemarvelous.utils.ConnectionUtils
import com.phuchienngo.marblemarvelous.utils.Console
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.util.Date
import javax.inject.Inject

class CloudsProvider(
    private val context: Context,
    private val openWeatherClouds: OpenWeatherClouds,
    private val apiKey: String,
    mainDispatcher: CoroutineDispatcher,
    private val callback: Callback
) {
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + mainDispatcher)

    @JvmField var lastUpdate: Date? = null

    interface Callback {
        fun onCloudsUpdated()
    }

    inner class CloudCubeMap
        @Throws(IOException::class)
        constructor(
            positiveX: FileHandle,
            negativeX: FileHandle,
            positiveY: FileHandle,
            negativeY: FileHandle,
            positiveZ: FileHandle,
            negativeZ: FileHandle
        ) : FacedCubemapData(
                null as TextureData?,
                null,
                null,
                null,
                null,
                null
            ) {
            private val pixMaps: Array<Pixmap>

            init {
                try {
                    pixMaps =
                        arrayOf(
                            Pixmap(positiveX),
                            Pixmap(negativeX),
                            Pixmap(positiveY),
                            Pixmap(negativeY),
                            Pixmap(positiveZ),
                            Pixmap(negativeZ)
                        )
                    val useMipMaps: Boolean = false
                    val disposePixmap: Boolean = false
                    for (i in pixMaps.indices) {
                        data[i] = PixmapTextureData(pixMaps[i], null, useMipMaps, disposePixmap)
                    }
                } catch (e: GdxRuntimeException) {
                    throw IOException("Error when loading cubemap data")
                }
            }

            fun dispose() {
                for (pixMap in pixMaps) {
                    pixMap.dispose()
                }
            }
        }

    fun updateClouds() {
        if (!ConnectionUtils.hasConnection(context)) {
            return
        }
        val now: Date = Date()
        if (CloudRefreshPolicy.shouldRefresh(lastUpdate, now)) {
            lastUpdate = now
            runCloudUpdate()
        }
    }

    fun getLatest(): CubemapData? {
        for (face in FACE_NAMES) {
            val file: File = File(context.cacheDir, "$face.png")
            if (!file.exists() || !file.canRead()) {
                return getBundledClouds()
            }
        }
        return try {
            CloudCubeMap(
                Gdx.files.absolute(context.cacheDir.toString() + "/px.png"),
                Gdx.files.absolute(context.cacheDir.toString() + "/nx.png"),
                Gdx.files.absolute(context.cacheDir.toString() + "/py.png"),
                Gdx.files.absolute(context.cacheDir.toString() + "/ny.png"),
                Gdx.files.absolute(context.cacheDir.toString() + "/pz.png"),
                Gdx.files.absolute(context.cacheDir.toString() + "/nz.png")
            )
        } catch (e: IOException) {
            Log.w(TAG, e)
            getBundledClouds()
        }
    }

    private fun getBundledClouds(): CubemapData {
        val useMipMaps: Boolean = false
        return KTXTextureData(Gdx.files.internal("earth/clouds.ktx"), useMipMaps)
    }

    private fun runCloudUpdate() {
        scope.launch updateClouds@{
            // Clouds come from OpenWeatherMap (original Google endpoint is dead).
            // generateCubeFaces is suspend and handles IO/CPU dispatching internally.
            val ok: Boolean = openWeatherClouds.generateCubeFaces(context, apiKey)
            val message: String =
                if (ok) {
                    "OpenWeather clouds updated."
                } else {
                    "OpenWeather clouds unavailable."
                }
            Console.info(TAG, message)
            if (ok) {
                callback.onCloudsUpdated()
            } else {
                lastUpdate = null
                Console.info(TAG, "Can't download clouds, retrying soon")
            }
            return@updateClouds
        }
    }

    fun dispose() {
        scope.cancel()
    }

    class Factory
        @Inject
        constructor(
            private val context: Context,
            private val openWeatherClouds: OpenWeatherClouds,
            @param:OpenWeatherApiKey private val apiKey: String,
            @param:MainDispatcher private val mainDispatcher: CoroutineDispatcher
        ) {
            fun create(callback: Callback): CloudsProvider = CloudsProvider(context, openWeatherClouds, apiKey, mainDispatcher, callback)
        }

    companion object {
        private val FACE_NAMES: Array<String> = arrayOf("px", "nx", "py", "ny", "pz", "nz")
        private const val TAG: String = "CM"
    }
}

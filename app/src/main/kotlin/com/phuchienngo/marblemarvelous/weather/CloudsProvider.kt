package com.phuchienngo.marblemarvelous.weather

import android.content.Context
import android.util.Log
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.CubemapData
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL30
import com.badlogic.gdx.graphics.glutils.KTXTextureData
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
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
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
            private val rawFaces: Array<File>
        ) : CubemapData {
            private var prepared: Boolean = false

            init {
                validateRawFaces(rawFaces)
            }

            override fun isPrepared(): Boolean = prepared

            override fun prepare() {
                try {
                    validateRawFaces(rawFaces)
                    prepared = true
                } catch (e: IOException) {
                    throw GdxRuntimeException("Error when preparing cloud cubemap data", e)
                }
            }

            override fun consumeCubemapData() {
                if (!prepared) {
                    throw GdxRuntimeException("Call prepare() before consumeCubemapData().")
                }

                try {
                    Gdx.gl.glPixelStorei(GL20.GL_UNPACK_ALIGNMENT, 1)
                    for (faceIndex: Int in rawFaces.indices) {
                        uploadFace(faceIndex, rawFaces[faceIndex])
                    }
                } catch (e: IOException) {
                    throw GdxRuntimeException("Error when loading cubemap data", e)
                } finally {
                    prepared = false
                }
            }

            override fun getWidth(): Int = FACE_SIZE

            override fun getHeight(): Int = FACE_SIZE

            override fun isManaged(): Boolean = false

            fun dispose() {
            }

            private fun uploadFace(
                faceIndex: Int,
                file: File
            ) {
                val target: Int = GL20.GL_TEXTURE_CUBE_MAP_POSITIVE_X + faceIndex
                Gdx.gl.glTexImage2D(
                    target,
                    0,
                    GL30.GL_R8,
                    FACE_SIZE,
                    FACE_SIZE,
                    0,
                    GL30.GL_RED,
                    GL20.GL_UNSIGNED_BYTE,
                    null
                )

                RandomAccessFile(file, READ_ONLY_MODE).use uploadRawFace@{ randomAccessFile: RandomAccessFile ->
                    randomAccessFile.channel.use uploadMappedFace@{ channel: FileChannel ->
                        val mappedFace: MappedByteBuffer =
                            channel.map(FileChannel.MapMode.READ_ONLY, 0, expectedRawFaceBytes())
                        Gdx.gl.glTexSubImage2D(
                            target,
                            0,
                            0,
                            0,
                            FACE_SIZE,
                            FACE_SIZE,
                            GL30.GL_RED,
                            GL20.GL_UNSIGNED_BYTE,
                            mappedFace
                        )
                        return@uploadMappedFace
                    }
                    return@uploadRawFace
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
        val rawFaces: Array<File> =
            Array(FACE_NAMES.size) { faceIndex: Int ->
                return@Array File(context.cacheDir, FACE_NAMES[faceIndex] + RAW_FACE_VERSION + RAW_FACE_EXTENSION)
            }
        for (file: File in rawFaces) {
            if (!isReadableRawFace(file)) {
                return getBundledClouds()
            }
        }
        return try {
            CloudCubeMap(rawFaces)
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
        private const val FACE_SIZE: Int = 512
        private const val RAW_FACE_VERSION: String = "-shape-v2"
        private const val RAW_FACE_EXTENSION: String = ".r8"
        private const val READ_ONLY_MODE: String = "r"
        private const val TAG: String = "CM"

        private fun isReadableRawFace(file: File): Boolean = file.exists() && file.canRead() && file.length() == expectedRawFaceBytes()

        private fun expectedRawFaceBytes(): Long = FACE_SIZE.toLong() * FACE_SIZE

        @Throws(IOException::class)
        private fun validateRawFaces(rawFaces: Array<File>) {
            if (rawFaces.size != FACE_NAMES.size) {
                throw IOException("Expected ${FACE_NAMES.size} cloud faces, got ${rawFaces.size}.")
            }
            for (file: File in rawFaces) {
                if (!isReadableRawFace(file)) {
                    throw IOException("Invalid raw cloud face ${file.name}, expected ${expectedRawFaceBytes()} bytes.")
                }
            }
        }
    }
}

package com.phuchienngo.marblemarvelous.filament

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar
import java.util.zip.CRC32

internal class FilamentEarthTextures private constructor(
  private val dayMap: Texture,
  private val nightMap: Texture,
  private var cloudMaskMap: Texture,
  private val cloudDetailMap: Texture,
  private val sampler: TextureSampler
) {
  // CRC of the six cached faces currently uploaded as the cloud mask. Lets a
  // repeat reload skip the GPU rebuild/swap when the faces are unchanged.
  private var cloudMaskSignature: Long? = null

  fun bind(materialInstance: MaterialInstance) {
    materialInstance.setParameter(FilamentEarthMaterial.DAY_MAP, dayMap, sampler)
    materialInstance.setParameter(FilamentEarthMaterial.NIGHT_MAP, nightMap, sampler)
    materialInstance.setParameter(FilamentEarthMaterial.CLOUD_MASK_MAP, cloudMaskMap, sampler)
    materialInstance.setParameter(FilamentEarthMaterial.CLOUD_DETAIL_MAP, cloudDetailMap, sampler)
  }

  fun destroy(engine: Engine) {
    engine.destroyTexture(dayMap)
    engine.destroyTexture(nightMap)
    if (cloudMaskMap !== cloudDetailMap) {
      engine.destroyTexture(cloudMaskMap)
    }
    engine.destroyTexture(cloudDetailMap)
  }

  /**
   * Swaps in the latest cached OpenWeather cloud faces. The blocking file read
   * runs off the main thread (see [readCloudFacesBuffer]); the Filament texture
   * is then built back on the caller's (render) thread. Returns false when no
   * cached faces are available yet, leaving the current mask untouched.
   */
  suspend fun reloadCloudMask(
    context: Context,
    engine: Engine,
    materialInstance: MaterialInstance
  ): Boolean {
    val cachedFaces: CachedFaces = readCloudFacesBuffer(context) ?: return false
    if (cachedFaces.checksum == cloudMaskSignature) {
      // Freshly cached faces are byte-identical to what's already shown; skip
      // the texture rebuild/swap and the follow-up render.
      return false
    }
    val newCloudMaskMap: Texture = buildCloudMaskTexture(engine, cachedFaces.buffer)
    val previousCloudMaskMap: Texture = cloudMaskMap

    try {
      materialInstance.setParameter(
        FilamentEarthMaterial.CLOUD_MASK_MAP,
        newCloudMaskMap,
        sampler
      )
    } catch (throwable: Throwable) {
      engine.destroyTexture(newCloudMaskMap)
      throw throwable
    }
    cloudMaskMap = newCloudMaskMap
    cloudMaskSignature = cachedFaces.checksum

    if (previousCloudMaskMap !== cloudDetailMap) {
      engine.destroyTexture(previousCloudMaskMap)
    }
    return true
  }

  companion object {
    // The cloud mask starts on the bundled detail map and is upgraded to the
    // real OpenWeather faces asynchronously via reloadCloudMask, so construction
    // never blocks the render thread on the cached-face file read.
    fun create(
      context: Context,
      engine: Engine
    ): FilamentEarthTextures {
      FilamentRuntime.initialize()
      val month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1
      val dayMap: Texture =
        loadKtxTexture(
          context = context,
          engine = engine,
          assetPath = FilamentEarthAssetPaths.dayMapForMonth(month)
        )
      val nightMap: Texture =
        loadKtxTexture(
          context = context,
          engine = engine,
          assetPath = FilamentEarthAssetPaths.NIGHT_MAP
        )
      val cloudDetailMap: Texture =
        loadKtxTexture(
          context = context,
          engine = engine,
          assetPath = FilamentEarthAssetPaths.CLOUD_DETAIL_MAP
        )
      return FilamentEarthTextures(
        dayMap = dayMap,
        nightMap = nightMap,
        cloudMaskMap = cloudDetailMap,
        cloudDetailMap = cloudDetailMap,
        sampler =
          TextureSampler(
            TextureSampler.MinFilter.LINEAR,
            TextureSampler.MagFilter.LINEAR,
            TextureSampler.WrapMode.CLAMP_TO_EDGE
          )
      )
    }

    private fun loadKtxTexture(
      context: Context,
      engine: Engine,
      assetPath: String
    ): Texture {
      val uploadBuffer: ByteBuffer = FilamentDirectBuffers.fromAsset(context, assetPath)
      return FilamentKtxCubeTextureArrayLoader.createTexture(engine, uploadBuffer)
    }

    /**
     * Reads the six cached faces into one direct buffer (with a content
     * checksum), or null if any is missing. The whole read — readability
     * checks, the direct allocation, the six stream copies and the checksum —
     * runs off the main thread in a single IO context.
     */
    private suspend fun readCloudFacesBuffer(context: Context): CachedFaces? =
      withContext(Dispatchers.IO) {
        val rawFaces: Array<File> =
          Array(FACE_NAMES.size) { faceIndex: Int ->
            return@Array File(
              context.cacheDir,
              FACE_NAMES[faceIndex] + RAW_FACE_VERSION + RAW_FACE_EXTENSION
            )
          }
        for (file: File in rawFaces) {
          if (!isReadableRawFace(file)) {
            return@withContext null
          }
        }

        val uploadBuffer: ByteBuffer =
          ByteBuffer
            .allocateDirect(FACE_NAMES.size * FACE_SIZE * FACE_SIZE)
            .order(ByteOrder.nativeOrder())
        for (file: File in rawFaces) {
          readFileInto(file, uploadBuffer)
        }
        uploadBuffer.flip()
        CachedFaces(uploadBuffer, checksumOf(uploadBuffer))
      }

    /** Filament resource creation — must run on the engine (render) thread. */
    private fun buildCloudMaskTexture(
      engine: Engine,
      uploadBuffer: ByteBuffer
    ): Texture {
      val texture: Texture =
        Texture
          .Builder()
          .sampler(Texture.Sampler.SAMPLER_2D_ARRAY)
          .usage(Texture.Usage.UPLOADABLE or Texture.Usage.SAMPLEABLE)
          .format(Texture.InternalFormat.R8)
          .width(FACE_SIZE)
          .height(FACE_SIZE)
          .depth(FACE_NAMES.size)
          .levels(1)
          .build(engine)
      texture.setTextureArrayImage(engine, uploadBuffer)
      return texture
    }

    /** CRC over the buffer's remaining bytes; restores its position afterwards. */
    private fun checksumOf(buffer: ByteBuffer): Long {
      val checksum = CRC32()
      buffer.mark()
      checksum.update(buffer)
      buffer.reset()
      return checksum.value
    }

    /** Streams one face into [buffer]. Caller runs it inside an IO context. */
    private fun readFileInto(
      file: File,
      buffer: ByteBuffer
    ) {
      FileInputStream(file).use { inputStream ->
        val channel = inputStream.channel
        while (buffer.hasRemaining() && channel.read(buffer) >= 0) {
          // Streams the face straight into the direct buffer.
        }
      }
    }

    private fun isReadableRawFace(file: File): Boolean =
      file.exists() && file.canRead() && file.length() == expectedRawFaceBytes()

    private fun expectedRawFaceBytes(): Long = FACE_SIZE.toLong() * FACE_SIZE

    @Suppress("DEPRECATION")
    private fun Texture.setTextureArrayImage(
      engine: Engine,
      uploadBuffer: ByteBuffer
    ) {
      val descriptor =
        Texture.PixelBufferDescriptor(
          uploadBuffer,
          Texture.Format.R,
          Texture.Type.UBYTE,
          1
        )
      // See FilamentUploadBuffers: the callback lets Filament release the upload
      // buffer once the async GPU upload finishes.
      descriptor.setCallback(null, FilamentUploadBuffers.RELEASE_AFTER_UPLOAD)
      setImage(
        engine,
        Texture.BASE_LEVEL,
        0,
        0,
        0,
        FACE_SIZE,
        FACE_SIZE,
        FACE_NAMES.size,
        descriptor
      )
    }

    private class CachedFaces(
      val buffer: ByteBuffer,
      val checksum: Long
    )

    private val FACE_NAMES: Array<String> = arrayOf("px", "nx", "py", "ny", "pz", "nz")
    private const val FACE_SIZE: Int = 512
    private const val RAW_FACE_EXTENSION: String = ".r8"
    private const val RAW_FACE_VERSION: String = "-shape-v2"
  }
}

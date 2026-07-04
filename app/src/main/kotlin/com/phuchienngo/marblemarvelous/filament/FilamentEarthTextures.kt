package com.phuchienngo.marblemarvelous.filament

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

internal class FilamentEarthTextures private constructor(
  private val dayMap: Texture,
  private val nightMap: Texture,
  private var cloudMaskMap: Texture,
  private val cloudDetailMap: Texture,
  private val sampler: TextureSampler,
  private val retainedUploadBuffers: List<ByteBuffer>,
  private var rawCloudUploadBuffer: ByteBuffer?
) {
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
    retainedUploadBuffers.forEach { uploadBuffer -> uploadBuffer.clear() }
    rawCloudUploadBuffer?.clear()
  }

  fun reloadCloudMask(
    context: Context,
    engine: Engine,
    materialInstance: MaterialInstance
  ): Boolean {
    val rawCloudTexture: RawCloudTexture = loadCachedCloudMask(context, engine) ?: return false
    val previousCloudMaskMap: Texture = cloudMaskMap
    val previousRawCloudUploadBuffer: ByteBuffer? = rawCloudUploadBuffer

    cloudMaskMap = rawCloudTexture.texture
    rawCloudUploadBuffer = rawCloudTexture.uploadBuffer
    materialInstance.setParameter(FilamentEarthMaterial.CLOUD_MASK_MAP, cloudMaskMap, sampler)

    if (previousCloudMaskMap !== cloudDetailMap) {
      engine.destroyTexture(previousCloudMaskMap)
    }
    previousRawCloudUploadBuffer?.clear()
    return true
  }

  companion object {
    fun create(
      context: Context,
      engine: Engine
    ): FilamentEarthTextures {
      FilamentRuntime.initialize()
      val month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1
      val dayMap: LoadedKtxTexture =
        loadKtxTexture(
          context = context,
          engine = engine,
          assetPath = FilamentEarthAssetPaths.dayMapForMonth(month)
        )
      val nightMap: LoadedKtxTexture =
        loadKtxTexture(
          context = context,
          engine = engine,
          assetPath = FilamentEarthAssetPaths.NIGHT_MAP
        )
      val cloudDetailMap: LoadedKtxTexture =
        loadKtxTexture(
          context = context,
          engine = engine,
          assetPath = FilamentEarthAssetPaths.CLOUD_DETAIL_MAP
        )
      val rawCloudTexture: RawCloudTexture? = loadCachedCloudMask(context, engine)
      val cloudMaskMap: Texture = rawCloudTexture?.texture ?: cloudDetailMap.texture
      return FilamentEarthTextures(
        dayMap = dayMap.texture,
        nightMap = nightMap.texture,
        cloudMaskMap = cloudMaskMap,
        cloudDetailMap = cloudDetailMap.texture,
        sampler =
          TextureSampler(
            TextureSampler.MinFilter.LINEAR,
            TextureSampler.MagFilter.LINEAR,
            TextureSampler.WrapMode.CLAMP_TO_EDGE
          ),
        retainedUploadBuffers =
          listOf(
            dayMap.uploadBuffer,
            nightMap.uploadBuffer,
            cloudDetailMap.uploadBuffer
          ),
        rawCloudUploadBuffer = rawCloudTexture?.uploadBuffer
      )
    }

    private fun loadKtxTexture(
      context: Context,
      engine: Engine,
      assetPath: String
    ): LoadedKtxTexture {
      val uploadBuffer: ByteBuffer = readAssetDirectBuffer(context, assetPath)
      return LoadedKtxTexture(
        texture = FilamentKtxCubeTextureArrayLoader.createTexture(engine, uploadBuffer),
        uploadBuffer = uploadBuffer
      )
    }

    private fun readAssetDirectBuffer(
      context: Context,
      assetPath: String
    ): ByteBuffer {
      val bytes: ByteArray =
        context.assets.open(assetPath).use readAsset@{ inputStream ->
          return@readAsset inputStream.readBytes()
        }
      val buffer: ByteBuffer =
        ByteBuffer
          .allocateDirect(bytes.size)
          .order(ByteOrder.nativeOrder())
      buffer.put(bytes)
      buffer.flip()
      return buffer
    }

    private fun loadCachedCloudMask(
      context: Context,
      engine: Engine
    ): RawCloudTexture? {
      val rawFaces: Array<File> =
        Array(FACE_NAMES.size) { faceIndex: Int ->
          return@Array File(
            context.cacheDir,
            FACE_NAMES[faceIndex] + RAW_FACE_VERSION + RAW_FACE_EXTENSION
          )
        }
      for (file: File in rawFaces) {
        if (!isReadableRawFace(file)) {
          return null
        }
      }

      val uploadBuffer: ByteBuffer =
        ByteBuffer
          .allocateDirect(FACE_NAMES.size * FACE_SIZE * FACE_SIZE)
          .order(ByteOrder.nativeOrder())
      for (file: File in rawFaces) {
        uploadBuffer.put(file.readBytes())
      }
      uploadBuffer.flip()

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
      return RawCloudTexture(texture, uploadBuffer)
    }

    private fun isReadableRawFace(file: File): Boolean =
      file.exists() && file.canRead() && file.length() == expectedRawFaceBytes()

    private fun expectedRawFaceBytes(): Long = FACE_SIZE.toLong() * FACE_SIZE

    @Suppress("DEPRECATION")
    private fun Texture.setTextureArrayImage(
      engine: Engine,
      uploadBuffer: ByteBuffer
    ) {
      setImage(
        engine,
        Texture.BASE_LEVEL,
        0,
        0,
        0,
        FACE_SIZE,
        FACE_SIZE,
        FACE_NAMES.size,
        Texture.PixelBufferDescriptor(
          uploadBuffer,
          Texture.Format.R,
          Texture.Type.UBYTE,
          1
        )
      )
    }

    private data class RawCloudTexture(
      val texture: Texture,
      val uploadBuffer: ByteBuffer
    )

    private data class LoadedKtxTexture(
      val texture: Texture,
      val uploadBuffer: ByteBuffer
    )

    private val FACE_NAMES: Array<String> = arrayOf("px", "nx", "py", "ny", "pz", "nz")
    private const val FACE_SIZE: Int = 512
    private const val RAW_FACE_EXTENSION: String = ".r8"
    private const val RAW_FACE_VERSION: String = "-shape-v2"
  }
}

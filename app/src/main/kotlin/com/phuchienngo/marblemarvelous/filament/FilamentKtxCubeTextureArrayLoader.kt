package com.phuchienngo.marblemarvelous.filament

import com.google.android.filament.Engine
import com.google.android.filament.Texture
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object FilamentKtxCubeTextureArrayLoader {
  fun createTexture(
    engine: Engine,
    buffer: ByteBuffer
  ): Texture {
    val reader: ByteBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    validateIdentifier(reader)

    reader.position(HEADER_FIELDS_OFFSET)
    val endianness: Int = reader.int
    require(endianness == LITTLE_ENDIAN_MARKER) {
      "Only little-endian KTX1 files are supported"
    }
    val glType: Int = reader.int
    reader.int
    val glFormat: Int = reader.int
    val glInternalFormat: Int = reader.int
    reader.int
    val pixelWidth: Int = reader.int
    val pixelHeight: Int = reader.int
    val pixelDepth: Int = reader.int
    val arrayElements: Int = reader.int
    val faceCount: Int = reader.int
    val mipLevelCount: Int = reader.int
    val keyValueBytes: Int = reader.int

    require(glType == COMPRESSED_GL_TYPE && glFormat == COMPRESSED_GL_FORMAT) {
      "Only compressed KTX1 cubemaps are supported"
    }
    require(pixelDepth == 0 && arrayElements == 0 && faceCount == CUBEMAP_FACE_COUNT) {
      "KTX1 texture must be a cubemap"
    }
    require(pixelWidth == pixelHeight && pixelWidth > 0) {
      "KTX1 cubemap faces must be square"
    }
    require(mipLevelCount > 0) {
      "KTX1 cubemap must contain explicit mip levels"
    }

    val format: Texture.InternalFormat = internalFormat(glInternalFormat)
    val compressedFormat: Texture.CompressedFormat = compressedFormat(glInternalFormat)
    require(Texture.isTextureFormatSupported(engine, format)) {
      "KTX1 compressed texture format $format is not supported by ${engine.backend}"
    }

    val texture: Texture =
      Texture
        .Builder()
        .sampler(Texture.Sampler.SAMPLER_2D_ARRAY)
        .usage(Texture.Usage.UPLOADABLE or Texture.Usage.SAMPLEABLE)
        .format(format)
        .width(pixelWidth)
        .height(pixelHeight)
        .depth(CUBEMAP_FACE_COUNT)
        .levels(mipLevelCount)
        .build(engine)

    var offset: Int = HEADER_BYTES + keyValueBytes
    for (level in 0 until mipLevelCount) {
      reader.position(offset)
      val faceBytes: Int = reader.int
      offset += INT_BYTES
      val facePadding: Int = padding4(faceBytes)
      val levelBytes: Int = faceBytes * CUBEMAP_FACE_COUNT
      val levelBuffer: ByteBuffer = bufferSlice(buffer, offset, levelBytes)
      texture.setTextureArrayLevel(
        engine = engine,
        level = level,
        buffer = levelBuffer,
        compressedFormat = compressedFormat,
        levelBytes = levelBytes
      )
      offset += (faceBytes + facePadding) * CUBEMAP_FACE_COUNT
      offset += padding4(offset)
    }
    return texture
  }

  private fun validateIdentifier(reader: ByteBuffer) {
    for (index in KTX_IDENTIFIER.indices) {
      require(reader.get(index) == KTX_IDENTIFIER[index]) {
        "Invalid KTX1 identifier"
      }
    }
  }

  private fun bufferSlice(
    buffer: ByteBuffer,
    offset: Int,
    byteCount: Int
  ): ByteBuffer {
    val duplicate: ByteBuffer = buffer.duplicate()
    duplicate.position(offset)
    duplicate.limit(offset + byteCount)
    return duplicate.slice().order(ByteOrder.nativeOrder())
  }

  private fun internalFormat(glInternalFormat: Int): Texture.InternalFormat =
    when (glInternalFormat) {
      GL_COMPRESSED_RGB8_ETC2 -> Texture.InternalFormat.ETC2_RGB8
      GL_COMPRESSED_SRGB8_ETC2 -> Texture.InternalFormat.ETC2_SRGB8
      else -> error("Unsupported KTX1 compressed internal format: $glInternalFormat")
    }

  private fun compressedFormat(glInternalFormat: Int): Texture.CompressedFormat =
    when (glInternalFormat) {
      GL_COMPRESSED_RGB8_ETC2 -> Texture.CompressedFormat.ETC2_RGB8
      GL_COMPRESSED_SRGB8_ETC2 -> Texture.CompressedFormat.ETC2_SRGB8
      else -> error("Unsupported KTX1 compressed format: $glInternalFormat")
    }

  private fun padding4(value: Int): Int = (FOUR_BYTE_ALIGNMENT - value % FOUR_BYTE_ALIGNMENT) % FOUR_BYTE_ALIGNMENT

  @Suppress("DEPRECATION")
  private fun Texture.setTextureArrayLevel(
    engine: Engine,
    level: Int,
    buffer: ByteBuffer,
    compressedFormat: Texture.CompressedFormat,
    levelBytes: Int
  ) {
    val descriptor = Texture.PixelBufferDescriptor(buffer, compressedFormat, levelBytes)
    // A callback makes Filament hold a reference to the upload buffer until the
    // async GPU upload completes, then release it (so it can be reclaimed).
    descriptor.setCallback(null, FilamentUploadBuffers.RELEASE_AFTER_UPLOAD)
    setImage(
      engine,
      level,
      0,
      0,
      0,
      getWidth(level),
      getHeight(level),
      CUBEMAP_FACE_COUNT,
      descriptor
    )
  }

  private val KTX_IDENTIFIER: ByteArray =
    byteArrayOf(
      0xAB.toByte(),
      0x4B,
      0x54,
      0x58,
      0x20,
      0x31,
      0x31,
      0xBB.toByte(),
      0x0D,
      0x0A,
      0x1A,
      0x0A
    )
  private const val COMPRESSED_GL_FORMAT: Int = 0
  private const val COMPRESSED_GL_TYPE: Int = 0
  private const val CUBEMAP_FACE_COUNT: Int = 6
  private const val FOUR_BYTE_ALIGNMENT: Int = 4
  private const val GL_COMPRESSED_RGB8_ETC2: Int = 0x9274
  private const val GL_COMPRESSED_SRGB8_ETC2: Int = 0x9275
  private const val HEADER_BYTES: Int = 64
  private const val HEADER_FIELDS_OFFSET: Int = 12
  private const val INT_BYTES: Int = 4
  private const val LITTLE_ENDIAN_MARKER: Int = 0x04030201
}

package com.phuchienngo.marblemarvelous.filament

import android.content.Context
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import java.nio.channels.ReadableByteChannel

internal object FilamentDirectBuffers {
  /**
   * Reads a bundled asset straight into a native (direct) [ByteBuffer].
   *
   * If the asset is stored uncompressed its length is known up-front, so the
   * bytes are streamed into a single pre-sized direct buffer with only a small
   * transient transfer window. Compressed assets (the default — the large KTX
   * textures compress ~4x in the APK) fall back to a heap read then copy.
   */
  fun fromAsset(
    context: Context,
    assetPath: String
  ): ByteBuffer {
    val declaredLength: Int = declaredAssetLength(context, assetPath)
    if (declaredLength >= 0) {
      context.assets.open(assetPath).use { inputStream ->
        return streamIntoDirectBuffer(inputStream, declaredLength)
      }
    }
    val bytes: ByteArray =
      context.assets.open(assetPath).use { inputStream ->
        return@use inputStream.readBytes()
      }
    return directBufferOf(bytes)
  }

  fun directBufferOf(bytes: ByteArray): ByteBuffer {
    val buffer: ByteBuffer =
      ByteBuffer
        .allocateDirect(bytes.size)
        .order(ByteOrder.nativeOrder())
    buffer.put(bytes)
    buffer.flip()
    return buffer
  }

  private fun declaredAssetLength(
    context: Context,
    assetPath: String
  ): Int =
    try {
      context.assets.openFd(assetPath).use { descriptor ->
        val length: Long = descriptor.length
        if (length in 0..Int.MAX_VALUE.toLong()) length.toInt() else -1
      }
    } catch (_: IOException) {
      // Compressed assets cannot be opened as a file descriptor.
      -1
    }

  private fun streamIntoDirectBuffer(
    inputStream: InputStream,
    length: Int
  ): ByteBuffer {
    val buffer: ByteBuffer =
      ByteBuffer
        .allocateDirect(length)
        .order(ByteOrder.nativeOrder())
    val channel: ReadableByteChannel = Channels.newChannel(inputStream)
    while (buffer.hasRemaining()) {
      if (channel.read(buffer) < 0) {
        break
      }
    }
    buffer.flip()
    return buffer
  }
}

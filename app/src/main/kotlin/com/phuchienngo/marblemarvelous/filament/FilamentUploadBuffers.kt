package com.phuchienngo.marblemarvelous.filament

internal object FilamentUploadBuffers {
  /**
   * Attaching a callback to a [com.google.android.filament.Texture.PixelBufferDescriptor]
   * makes Filament retain the backing [java.nio.ByteBuffer] (via a JNI global
   * reference) until the asynchronous GPU upload finishes, at which point it
   * invokes this callback and drops the reference so the buffer can be
   * reclaimed. No extra bookkeeping is required on our side, so the callback
   * itself is a no-op.
   */
  val RELEASE_AFTER_UPLOAD: Runnable = Runnable {}
}

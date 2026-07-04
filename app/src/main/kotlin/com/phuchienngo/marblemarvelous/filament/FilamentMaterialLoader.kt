package com.phuchienngo.marblemarvelous.filament

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.Material
import java.nio.ByteBuffer

internal object FilamentMaterialLoader {
  fun load(
    context: Context,
    engine: Engine,
    assetPath: String
  ): Material {
    FilamentRuntime.initialize()
    val payload: ByteBuffer = FilamentDirectBuffers.fromAsset(context, assetPath)
    return Material
      .Builder()
      .payload(payload, payload.remaining())
      .build(engine)
  }
}

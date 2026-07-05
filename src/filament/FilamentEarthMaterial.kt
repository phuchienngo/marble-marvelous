package com.phuchienngo.marblemarvelous.filament

import android.content.Context
import com.google.android.filament.Engine
import com.google.android.filament.Material

internal object FilamentEarthMaterial {
  fun create(
    context: Context,
    engine: Engine
  ): Material = FilamentMaterialLoader.load(context, engine, ASSET_PATH)

  const val ASSET_PATH: String = "filament/earth.filamat"
  const val CLOUD_DETAIL_MAP: String = "cloudDetailMap"
  const val CLOUD_MASK_MAP: String = "cloudMaskMap"
  const val DAY_MAP: String = "dayMap"
  const val NIGHT_MAP: String = "nightMap"
  const val SUN_DIRECTION: String = "sunDirection"
  const val USER_LOCATION: String = "userLocation"
  const val TIME: String = "time"
  const val AURORA_ACTIVITY: String = "auroraActivity"
}

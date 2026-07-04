package com.phuchienngo.marblemarvelous.filament

internal object FilamentEarthAssetPaths {
  const val CLOUD_DETAIL_MAP: String = "earth/clouds.ktx"
  const val NIGHT_MAP: String = "earth/nightMap.ktx"

  fun dayMapForMonth(month: Int): String {
    require(month in MIN_MONTH..MAX_MONTH) {
      "month must be in 1..12"
    }
    return when (month) {
      !in MARCH..NOVEMBER -> "earth/dayMap-Winter.ktx"
      in JUNE..<SEPTEMBER -> "earth/dayMap-Summer.ktx"
      else -> "earth/dayMap-Spring-Fall.ktx"
    }
  }

  private const val MARCH: Int = 3
  private const val MAX_MONTH: Int = 12
  private const val JUNE: Int = 6
  private const val MIN_MONTH: Int = 1
  private const val NOVEMBER: Int = 11
  private const val SEPTEMBER: Int = 9
}

package com.phuchienngo.marblemarvelous.filament

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class FilamentEarthAssetPathsTest {
  @Test
  fun usesEveryBundledSeasonalDayMapAsset() {
    val bundledDayMaps: Set<String> =
      earthAssetDirectory()
        .listFiles { file: File ->
          file.name.startsWith(DAY_MAP_PREFIX) && file.name.endsWith(KTX_EXTENSION)
        }
        ?.map { file: File -> "earth/${file.name}" }
        ?.toSet()
        ?: emptySet()
    val usedDayMaps: Set<String> =
      (1..12)
        .map { month: Int -> FilamentEarthAssetPaths.dayMapForMonth(month) }
        .toSet()

    assertEquals(
      "Every bundled dayMap-*.ktx asset should be reachable from dayMapForMonth.",
      bundledDayMaps,
      usedDayMaps
    )
  }

  @Test
  fun picksWinterDayMapForDecemberJanuaryAndFebruary() {
    assertEquals("earth/dayMap-Winter.ktx", FilamentEarthAssetPaths.dayMapForMonth(12))
    assertEquals("earth/dayMap-Winter.ktx", FilamentEarthAssetPaths.dayMapForMonth(1))
    assertEquals("earth/dayMap-Winter.ktx", FilamentEarthAssetPaths.dayMapForMonth(2))
  }

  @Test
  fun picksSummerDayMapForJuneJulyAndAugust() {
    assertEquals("earth/dayMap-Summer.ktx", FilamentEarthAssetPaths.dayMapForMonth(6))
    assertEquals("earth/dayMap-Summer.ktx", FilamentEarthAssetPaths.dayMapForMonth(7))
    assertEquals("earth/dayMap-Summer.ktx", FilamentEarthAssetPaths.dayMapForMonth(8))
  }

  @Test
  fun picksSpringFallDayMapForRemainingMonths() {
    assertEquals("earth/dayMap-Spring-Fall.ktx", FilamentEarthAssetPaths.dayMapForMonth(3))
    assertEquals("earth/dayMap-Spring-Fall.ktx", FilamentEarthAssetPaths.dayMapForMonth(5))
    assertEquals("earth/dayMap-Spring-Fall.ktx", FilamentEarthAssetPaths.dayMapForMonth(9))
    assertEquals("earth/dayMap-Spring-Fall.ktx", FilamentEarthAssetPaths.dayMapForMonth(11))
  }

  @Test(expected = IllegalArgumentException::class)
  fun rejectsInvalidMonth() {
    FilamentEarthAssetPaths.dayMapForMonth(13)
  }

  private fun earthAssetDirectory(): File {
    val candidates: List<File> =
      listOf(
        File("app/src/main/assets/earth"),
        File("src/main/assets/earth")
      )
    return candidates.firstOrNull { file: File -> file.isDirectory }
      ?: error("Unable to find earth asset directory from ${File(".").absolutePath}")
  }

  private companion object {
    const val DAY_MAP_PREFIX: String = "dayMap-"
    const val KTX_EXTENSION: String = ".ktx"
  }
}

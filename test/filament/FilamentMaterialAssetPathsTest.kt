package com.phuchienngo.marblemarvelous.filament

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FilamentMaterialAssetPathsTest {
  @Test
  fun precompiledMaterialAssetsExist() {
    val materialAssetPaths: Set<String> =
      setOf(
        FilamentEarthMaterial.ASSET_PATH,
        FilamentStars.MATERIAL_ASSET_PATH
      )

    for (assetPath in materialAssetPaths) {
      val assetFile = File(assetDirectory(), assetPath)
      assertTrue("$assetPath should exist", assetFile.isFile)
      assertTrue("$assetPath should not be empty", assetFile.length() > 0L)
    }
  }

  private fun assetDirectory(): File {
    val dir = File("assets")
    return dir.takeIf { it.isDirectory }
      ?: error("Unable to find main asset directory from ${File(".").absolutePath}")
  }
}

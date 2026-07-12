package com.phuchienngo.marblemarvelous.filament

import com.google.android.filament.TextureSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FilamentMaterialAssetPathsTest {
  @Test
  fun surfaceTexturesUseMipFiltering() {
    assertEquals(
      TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
      FilamentEarthTextures.SURFACE_MIN_FILTER
    )
    assertEquals(4.0f, FilamentEarthTextures.SURFACE_ANISOTROPY, 0.0f)
  }

  @Test
  fun precompiledMaterialAssetsExist() {
    val materialAssetPaths: Set<String> =
      setOf(
        FilamentEarthMaterial.ASSET_PATH,
        FilamentEarthMaterial.CLOUD_SHELL_ASSET_PATH,
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

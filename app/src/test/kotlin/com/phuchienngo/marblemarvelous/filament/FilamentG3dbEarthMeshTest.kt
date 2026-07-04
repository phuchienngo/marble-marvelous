package com.phuchienngo.marblemarvelous.filament

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilamentG3dbEarthMeshTest {
  @Test
  fun loadsOriginalEarthMeshWithExpectedCounts() {
    val mesh: FilamentEarthMeshData =
      earthAsset().inputStream().use { input ->
        FilamentG3dbEarthMesh.load(input)
      }

    assertEquals(5122, mesh.vertexCount)
    assertEquals(29520, mesh.indexCount)
    assertEquals(mesh.vertexCount * 3, mesh.positions.size)
    assertEquals(mesh.vertexCount * 4, mesh.lookupNormals.size)
  }

  @Test
  fun copiesOriginalPositionsAndLookupNormals() {
    val mesh: FilamentEarthMeshData =
      earthAsset().inputStream().use { input ->
        FilamentG3dbEarthMesh.load(input)
      }

    assertEquals(-0.005078f, mesh.positions[0], EPSILON)
    assertEquals(-1.314069f, mesh.positions[1], EPSILON)
    assertEquals(-0.049502f, mesh.positions[2], EPSILON)

    assertEquals(-0.004874f, mesh.lookupNormals[0], EPSILON)
    assertEquals(-0.998859f, mesh.lookupNormals[1], EPSILON)
    assertEquals(-0.047508f, mesh.lookupNormals[2], EPSILON)
    assertEquals(1.0f, mesh.lookupNormals[3], EPSILON)
  }

  @Test
  fun keepsIndicesInsideVertexRange() {
    val mesh: FilamentEarthMeshData =
      earthAsset().inputStream().use { input ->
        FilamentG3dbEarthMesh.load(input)
      }

    for (index in mesh.indices) {
      val vertexIndex: Int = index.toInt() and UNSIGNED_SHORT_MASK
      assertTrue(vertexIndex >= 0)
      assertTrue(vertexIndex < mesh.vertexCount)
    }
  }

  private fun earthAsset(): File {
    val moduleRelativeAsset = File("src/main/assets/earth/earth.g3db")
    if (moduleRelativeAsset.exists()) {
      return moduleRelativeAsset
    }
    return File("app/src/main/assets/earth/earth.g3db")
  }

  companion object {
    private const val EPSILON: Float = 0.00001f
    private const val UNSIGNED_SHORT_MASK: Int = 0xFFFF
  }
}

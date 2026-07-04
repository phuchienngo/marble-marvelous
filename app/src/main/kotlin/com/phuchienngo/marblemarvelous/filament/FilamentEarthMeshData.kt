package com.phuchienngo.marblemarvelous.filament

internal data class FilamentEarthMeshData(
  val positions: FloatArray,
  val lookupNormals: FloatArray,
  val indices: ShortArray
) {
  val vertexCount: Int
    get() = positions.size / POSITION_COMPONENTS

  val indexCount: Int
    get() = indices.size

  val boundingHalfExtent: Float
    get() {
      var maxExtent = 0.0f
      for (position in positions) {
        val absolutePosition: Float = kotlin.math.abs(position)
        if (absolutePosition > maxExtent) {
          maxExtent = absolutePosition
        }
      }
      return maxExtent
    }

  companion object {
    const val LOOKUP_NORMAL_COMPONENTS: Int = 4
    const val POSITION_COMPONENTS: Int = 3
  }
}

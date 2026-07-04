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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FilamentEarthMeshData

    if (!positions.contentEquals(other.positions)) return false
    if (!lookupNormals.contentEquals(other.lookupNormals)) return false
    if (!indices.contentEquals(other.indices)) return false
    if (vertexCount != other.vertexCount) return false
    if (indexCount != other.indexCount) return false
    if (boundingHalfExtent != other.boundingHalfExtent) return false

    return true
  }

  override fun hashCode(): Int {
    var result = positions.contentHashCode()
    result = 31 * result + lookupNormals.contentHashCode()
    result = 31 * result + indices.contentHashCode()
    result = 31 * result + vertexCount
    result = 31 * result + indexCount
    result = 31 * result + boundingHalfExtent.hashCode()
    return result
  }
}

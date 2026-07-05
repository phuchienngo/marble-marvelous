package com.phuchienngo.marblemarvelous.math

import kotlin.math.sqrt

data class Vec3(
  val x: Float,
  val y: Float,
  val z: Float
) {
  fun add(other: Vec3): Vec3 =
    Vec3(
      x = x + other.x,
      y = y + other.y,
      z = z + other.z
    )

  fun cross(other: Vec3): Vec3 =
    Vec3(
      x = y * other.z - z * other.y,
      y = z * other.x - x * other.z,
      z = x * other.y - y * other.x
    )

  fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

  fun normalized(): Vec3 {
    val length: Float = sqrt(x * x + y * y + z * z)
    if (length == 0.0f) {
      return this
    }
    return scale(1.0f / length)
  }

  fun scale(value: Float): Vec3 =
    Vec3(
      x = x * value,
      y = y * value,
      z = z * value
    )

  companion object {
    val UP: Vec3 = Vec3(0.0f, 1.0f, 0.0f)
  }
}

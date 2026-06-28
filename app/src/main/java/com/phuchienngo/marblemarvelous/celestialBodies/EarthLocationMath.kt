package com.phuchienngo.marblemarvelous.celestialBodies

import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import kotlin.math.cos
import kotlin.math.sin

object EarthLocationMath {
    fun sunDeclination(dayOfYear: Int): Float =
        (AXIAL_TILT * cos(SOLAR_YEAR_RADIANS_PER_DAY * (dayOfYear + SOLSTICE_OFFSET_DAYS))).toFloat()

    fun sunLightPosition(sunDeclinationDegrees: Float): Vector3 = Vector3(INITIAL_LIGHT_POSITION).rotate(Vector3.X, sunDeclinationDegrees)

    fun locationSurface(
        longitudeDegrees: Float,
        latitudeDegrees: Float,
        radius: Float,
        earthTransform: Matrix4,
    ): Vector3 {
        val latitudeRadians: Double = Math.toRadians(latitudeDegrees.toDouble())
        val longitudeRadians: Double = Math.toRadians(longitudeDegrees.toDouble())
        return Vector3(
            cos(latitudeRadians).toFloat() * sin(longitudeRadians).toFloat(),
            sin(latitudeRadians).toFloat(),
            cos(latitudeRadians).toFloat() * cos(longitudeRadians).toFloat(),
        ).scl(radius).rot(earthTransform)
    }

    fun daylightFactor(
        longitudeDegrees: Float,
        latitudeDegrees: Float,
        utcDayRatio: Float,
        sunDeclinationDegrees: Float,
    ): Float {
        val earthTransform: Matrix4 = Matrix4().idt().rotate(Vector3.Y, FULL_ROTATION_DEGREES * utcDayRatio)
        val surface: Vector3 = locationSurface(longitudeDegrees, latitudeDegrees, UNIT_RADIUS, earthTransform).nor()
        val light: Vector3 = sunLightPosition(sunDeclinationDegrees).nor()
        return surface.dot(light)
    }

    private const val AXIAL_TILT: Double = -23.439281463623047
    private const val FULL_ROTATION_DEGREES: Float = 360.0f
    private const val SOLAR_YEAR_RADIANS_PER_DAY: Double = 0.01721420632103996
    private const val SOLSTICE_OFFSET_DAYS: Int = 10
    private const val UNIT_RADIUS: Float = 1.0f
    private val INITIAL_LIGHT_POSITION: Vector3 = Vector3(0.0f, 0.0f, -1.0f)
}

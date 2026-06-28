package com.phuchienngo.marblemarvelous.location

import com.badlogic.gdx.math.Vector2
import java.util.TimeZone

object LocationFallback {
    fun fromTimeZone(
        timeZone: TimeZone,
        timeInMillis: Long
    ): Vector2 = fromUtcOffset(timeZone.getOffset(timeInMillis))

    fun fromUtcOffset(offsetMillis: Int): Vector2 {
        val offsetHours: Float = offsetMillis / MILLIS_PER_HOUR
        val longitude: Float = (offsetHours * DEGREES_PER_HOUR).coerceIn(MIN_LONGITUDE, MAX_LONGITUDE)
        return Vector2(longitude, EQUATOR_LATITUDE)
    }

    private const val DEGREES_PER_HOUR: Float = 15.0f
    private const val EQUATOR_LATITUDE: Float = 0.0f
    private const val MAX_LONGITUDE: Float = 180.0f
    private const val MILLIS_PER_HOUR: Float = 3_600_000.0f
    private const val MIN_LONGITUDE: Float = -180.0f
}

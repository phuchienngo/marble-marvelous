package com.phuchienngo.marblemarvelous.weather

import kotlin.math.*

import android.icu.util.Calendar

object PhaseOfTheMoon {
    @JvmStatic
    fun getPhaseRatio(): Double {
        val calendar = Calendar.getInstance()
        val year = calendar.get(1).toDouble()
        val month = calendar.get(2).toDouble()
        val utcMillis = calendar.timeInMillis
        val millisInADay = 1000.0 * 86400.0
        val n = floor(((year - 1900.0) + (((1.0 * month) - 0.5) / 12.0)) * 12.37)
        val t = n / 1236.85
        val t2 = t * t
        val asValue = 359.2242 + (29.105356 * n)
        val am = 306.0253 + (385.816918 * n) + (0.01073 * t2)
        val xtra = 0.75933 + (1.53058868 * n) + ((1.178E-4 - (1.55E-7 * t)) * t2) +
            (((0.1734 - (3.93E-4 * t)) * sin(Math.toRadians(asValue))) -
                (0.4068 * sin(Math.toRadians(am))))
        val i = if (xtra > 0.0) floor(xtra) else ceil(xtra - 1.0)
        val j1 = 2440587.5 + (utcMillis / millisInADay)
        val jd = 2415020.0 + (28.0 * n) + i
        val moonDate = ((j1 - jd) + 30.0) % 30.0
        return moonDate / 30.0
    }
}

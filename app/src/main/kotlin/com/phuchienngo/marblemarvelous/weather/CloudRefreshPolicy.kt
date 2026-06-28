package com.phuchienngo.marblemarvelous.weather

import java.util.Date

internal object CloudRefreshPolicy {
    fun shouldRefresh(
        lastUpdate: Date?,
        now: Date
    ): Boolean {
        if (lastUpdate == null) {
            return true
        }

        return now.time - lastUpdate.time >= REFRESH_INTERVAL_MILLIS
    }

    private const val REFRESH_INTERVAL_MILLIS: Long = 60L * 60L * 1000L
}

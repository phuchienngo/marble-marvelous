package com.phuchienngo.marblemarvelous.weather

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class CloudRefreshPolicyTest {
    @Test
    fun shouldRefreshWhenCloudsAreOlderThanOneHour() {
        val now = Date(NOW_MILLIS)
        val recentUpdate = Date(NOW_MILLIS - FIFTY_NINE_MINUTES_MILLIS)
        val staleUpdate = Date(NOW_MILLIS - SIXTY_ONE_MINUTES_MILLIS)

        assertFalse(CloudRefreshPolicy.shouldRefresh(recentUpdate, now))
        assertTrue(CloudRefreshPolicy.shouldRefresh(staleUpdate, now))
        assertTrue(CloudRefreshPolicy.shouldRefresh(lastUpdate = null, now = now))
    }

    companion object {
        private const val NOW_MILLIS: Long = 3_600_000L
        private const val FIFTY_NINE_MINUTES_MILLIS: Long = 59L * 60L * 1000L
        private const val SIXTY_ONE_MINUTES_MILLIS: Long = 61L * 60L * 1000L
    }
}

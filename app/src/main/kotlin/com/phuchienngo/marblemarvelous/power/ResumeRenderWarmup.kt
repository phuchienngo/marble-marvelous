package com.phuchienngo.marblemarvelous.power

internal class ResumeRenderWarmup(
    private val clock: () -> Long,
    private val durationNanos: Long = DEFAULT_DURATION_NANOS
) {
    private var warmupEndNanos: Long = WARMUP_INACTIVE_NANOS

    fun markResumed() {
        warmupEndNanos = clock() + durationNanos
    }

    fun isActive(): Boolean = clock() < warmupEndNanos

    companion object {
        private const val DEFAULT_DURATION_NANOS: Long = 750L * 1_000_000L
        private const val WARMUP_INACTIVE_NANOS: Long = Long.MIN_VALUE
    }
}

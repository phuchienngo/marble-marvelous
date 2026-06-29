package com.phuchienngo.marblemarvelous.power

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FPSThrottlerSchedulingTest {
    @Test
    fun throttlerUsesDelayedHandlerTicksInsteadOfVsyncCallbacks() {
        val source: String = readThrottlerSource()

        assertFalse(source.contains("android.view.Choreographer"))
        assertFalse(source.contains("Choreographer.FrameCallback"))
        assertTrue(source.contains("sendEmptyMessageDelayed(REQUEST_RENDER_MESSAGE, frameDelayMs())"))
        assertTrue(source.contains("removeMessages(REQUEST_RENDER_MESSAGE)"))
    }

    @Test
    fun resumeDoesNotStartAnotherRenderThreadWhenOneIsAlreadyRunning() {
        val source: String = readThrottlerSource()

        assertTrue(source.contains("if (renderThread != null && renderThread.isAlive)"))
    }

    @Test
    fun enablingContinuousRenderingWakesDelayedLoopAfterScreenTurnsOn() {
        val source: String = readThrottlerSource()

        assertTrue(
            source.contains(
                "if (continuousRendering) {\n" +
                    "                mRenderThread?.requestTick()\n" +
                    "            }"
            )
        )
    }

    private fun readThrottlerSource(): String =
        File("src/main/kotlin/com/phuchienngo/marblemarvelous/power/FPSThrottler.kt")
            .readText()
}

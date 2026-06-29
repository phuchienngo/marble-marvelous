package com.phuchienngo.marblemarvelous.power

import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidGraphics
import com.phuchienngo.marblemarvelous.di.WallpaperEngineScope
import com.phuchienngo.marblemarvelous.utils.Console
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.roundToLong

@WallpaperEngineScope
class FPSThrottler
    @Inject
    constructor() {
        @JvmField var fps = 60
        private var isPowerSaveMode = false
        private val isDrawing: AtomicBoolean = AtomicBoolean(NOT_DRAWING)
        private val isContinuousRendering: AtomicBoolean = AtomicBoolean(CONTINUOUS_RENDERING_ENABLED)
        private val requestRender: AtomicBoolean = AtomicBoolean(RENDER_NOT_REQUESTED)

        @Volatile
        private var frameTimeMs = 16.666666666666668

        private var mRenderThread: FpsThrottlerThread? = null

        @Synchronized
        fun resume() {
            Gdx.app.graphics.isContinuousRendering = false
            val renderThread: FpsThrottlerThread? = mRenderThread
            if (renderThread != null && renderThread.isAlive) {
                updateFrameTime()
                renderThread.requestTick()
                return
            }
            requestRender.set(RENDER_NOT_REQUESTED)
            isDrawing.set(NOT_DRAWING)
            updateFrameTime()
            val nextRenderThread: FpsThrottlerThread = FpsThrottlerThread()
            mRenderThread = nextRenderThread
            nextRenderThread.start()
        }

        @Synchronized
        fun pause() {
            Gdx.app.graphics.isContinuousRendering = true
            val renderThread: FpsThrottlerThread? = mRenderThread
            if (renderThread != null) {
                renderThread.quit()
                mRenderThread = null
            }
            requestRender.set(RENDER_NOT_REQUESTED)
            requestRendering()
        }

        fun dispose() {
            pause()
        }

        fun beginFrame() {
            isDrawing.set(DRAWING)
        }

        fun endFrame(fps: Int) {
            this.fps =
                if (fps <= 0) {
                    1
                } else {
                    if (isPowerSaveMode) {
                        fps / 2
                    } else {
                        fps
                    }
                }
            updateFrameTime()
            isDrawing.set(NOT_DRAWING)
        }

        private fun updateFrameTime() {
            frameTimeMs = 1000.0 / fps.toDouble()
        }

        fun setPowerSaveMode(isPowerSaveMode: Boolean) {
            this.isPowerSaveMode = isPowerSaveMode
        }

        fun setContinuousRendering(continuousRendering: Boolean) {
            isContinuousRendering.set(continuousRendering)
            if (continuousRendering) {
                mRenderThread?.requestTick()
            }
        }

        fun requestRendering(force: Boolean) {
            if (!isContinuousRendering.get()) {
                requestRender.set(RENDER_REQUESTED)
                mRenderThread?.requestTick()
            }
            if (force) {
                requestRendering()
            }
        }

        private fun requestRendering() {
            ((Gdx.app.graphics as AndroidGraphics).view as GLSurfaceView).queueEvent requestRender@{
                return@requestRender Gdx.app.graphics.requestRendering()
            }
        }

        private class FpsHandler(
            looper: Looper,
            private val renderThread: FpsThrottlerThread
        ) : Handler(looper) {
            override fun handleMessage(msg: Message) {
                when (msg.what) {
                    STOP_MESSAGE -> {
                        removeMessages(REQUEST_RENDER_MESSAGE)
                        Looper.myLooper()?.quit()
                    }

                    REQUEST_RENDER_MESSAGE -> {
                        renderThread.handleFrameTick()
                    }

                    else -> {
                        return
                    }
                }
            }
        }

        private inner class FpsThrottlerThread :
            Thread() {
            private var lastFrameTimeNanos = -1L

            @Volatile
            private var mHandler: Handler? = null

            override fun run() {
                name = "FpsThrottlerThread"
                Looper.prepare()
                val looper: Looper =
                    requireNotNull(Looper.myLooper()) currentLooper@{
                        return@currentLooper "FpsThrottlerThread must prepare a Looper before creating its handler."
                    }
                val handler: Handler = FpsHandler(looper, this)
                mHandler = handler
                if (!isInterrupted) {
                    handler.sendEmptyMessage(REQUEST_RENDER_MESSAGE)
                    Looper.loop()
                } else {
                    looper.quit()
                }
                handler.removeMessages(REQUEST_RENDER_MESSAGE)
                mHandler = null
                Console.log(TAG, "looper quit")
            }

            fun requestTick() {
                val handler: Handler = mHandler ?: return
                handler.removeMessages(REQUEST_RENDER_MESSAGE)
                handler.sendEmptyMessage(REQUEST_RENDER_MESSAGE)
            }

            fun quit() {
                val handler: Handler? = mHandler
                if (handler != null) {
                    handler.removeMessages(REQUEST_RENDER_MESSAGE)
                    handler.sendEmptyMessage(STOP_MESSAGE)
                    return
                }
                interrupt()
            }

            fun handleFrameTick() {
                if (isInterrupted) {
                    Looper.myLooper()?.quit()
                    return
                }
                val frameTimeNanos: Long = SystemClock.elapsedRealtimeNanos()
                if (lastFrameTimeNanos == INITIAL_FRAME_TIME_NANOS) {
                    lastFrameTimeNanos = frameTimeNanos
                    requestRendering()
                    scheduleNextFrame()
                    return
                }

                if (isDrawing.get()) {
                    scheduleNextFrame()
                    return
                }

                val dms: Double = (frameTimeNanos - lastFrameTimeNanos) / NANOS_PER_MILLIS
                if (dms >= frameTimeMs) {
                    if (shouldRenderFrame()) {
                        requestRendering()
                        requestRender.compareAndSet(RENDER_REQUESTED, RENDER_NOT_REQUESTED)
                    }
                    lastFrameTimeNanos = frameTimeNanos
                }
                scheduleNextFrame()
            }

            private fun shouldRenderFrame(): Boolean {
                return isContinuousRendering.get() ||
                    (!isContinuousRendering.get() && requestRender.get())
            }

            private fun scheduleNextFrame() {
                val handler: Handler = mHandler ?: return
                handler.removeMessages(REQUEST_RENDER_MESSAGE)
                if (shouldScheduleNextFrame()) {
                    handler.sendEmptyMessageDelayed(REQUEST_RENDER_MESSAGE, frameDelayMs())
                }
            }

            private fun shouldScheduleNextFrame(): Boolean {
                return isContinuousRendering.get() || requestRender.get()
            }
        }

        private fun frameDelayMs(): Long =
            frameTimeMs
                .roundToLong()
                .coerceAtLeast(MIN_FRAME_DELAY_MS)

        companion object {
            private const val TAG: String = "FPSThrottler"
            private const val CONTINUOUS_RENDERING_ENABLED: Boolean = true
            private const val DRAWING: Boolean = true
            private const val INITIAL_FRAME_TIME_NANOS: Long = -1L
            private const val MIN_FRAME_DELAY_MS: Long = 1L
            private const val NANOS_PER_MILLIS: Double = 1000000.0
            private const val NOT_DRAWING: Boolean = false
            private const val RENDER_NOT_REQUESTED: Boolean = false
            private const val RENDER_REQUESTED: Boolean = true
            private const val REQUEST_RENDER_MESSAGE: Int = 1
            private const val STOP_MESSAGE: Int = 0
        }
    }

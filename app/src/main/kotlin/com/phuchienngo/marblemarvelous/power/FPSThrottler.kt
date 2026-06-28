package com.phuchienngo.marblemarvelous.power

import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.Choreographer
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.android.AndroidGraphics
import com.phuchienngo.marblemarvelous.di.WallpaperEngineScope
import com.phuchienngo.marblemarvelous.utils.Console
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

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

        fun resume() {
            Gdx.app.graphics.isContinuousRendering = false
            requestRender.set(RENDER_NOT_REQUESTED)
            isDrawing.set(NOT_DRAWING)
            updateFrameTime()
            mRenderThread = FpsThrottlerThread()
            mRenderThread!!.start()
        }

        fun pause() {
            Gdx.app.graphics.isContinuousRendering = true
            val renderThread: FpsThrottlerThread? = mRenderThread
            if (renderThread != null) {
                val handler: Handler? = renderThread.getHandler()
                if (handler != null) {
                    handler.sendEmptyMessage(0)
                } else {
                    renderThread.interrupt()
                }
                mRenderThread = null
            }
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
            frameTimeMs =
                1000.0 /
                    (
                        if (isContinuousRendering.get()) {
                            fps
                        } else {
                            DEFAULT_FPS
                        }
                    ).toDouble()
        }

        fun setPowerSaveMode(isPowerSaveMode: Boolean) {
            this.isPowerSaveMode = isPowerSaveMode
        }

        fun setContinuousRendering(continuousRendering: Boolean) {
            isContinuousRendering.set(continuousRendering)
        }

        fun requestRendering(force: Boolean) {
            if (!isContinuousRendering.get()) {
                requestRender.set(RENDER_REQUESTED)
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
            looper: Looper
        ) : Handler(looper) {
            override fun handleMessage(msg: Message) {
                Console.log(TAG, "got message, quitting")
                Looper.myLooper()?.quit()
            }
        }

        private inner class FpsThrottlerThread :
            Thread(),
            Choreographer.FrameCallback {
            private var lastFrameTimeNanos = -1L

            @Volatile
            private var mHandler: Handler? = null

            override fun run() {
                name = "FpsThrottlerThread"
                Looper.prepare()
                mHandler = FpsHandler(Looper.myLooper()!!)
                Choreographer.getInstance().postFrameCallback(this)
                if (isInterrupted) {
                    Looper.myLooper()?.quit()
                } else {
                    Looper.loop()
                }
                Console.log(TAG, "looper quit")
                Choreographer.getInstance().removeFrameCallback(this)
            }

            fun getHandler(): Handler? = mHandler

            override fun doFrame(frameTimeNanos: Long) {
                if (lastFrameTimeNanos == INITIAL_FRAME_TIME_NANOS) {
                    lastFrameTimeNanos = frameTimeNanos
                    requestRendering()
                    Choreographer.getInstance().postFrameCallback(this)
                    return
                }

                if (isDrawing.get()) {
                    Choreographer.getInstance().postFrameCallback(this)
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
                Choreographer.getInstance().postFrameCallback(this)
            }

            private fun shouldRenderFrame(): Boolean {
                return isContinuousRendering.get() ||
                    (!isContinuousRendering.get() && requestRender.get())
            }
        }

        companion object {
            private const val TAG: String = "FPSThrottler"
            private const val CONTINUOUS_RENDERING_ENABLED: Boolean = true
            private const val DEFAULT_FPS: Int = 60
            private const val DRAWING: Boolean = true
            private const val INITIAL_FRAME_TIME_NANOS: Long = -1L
            private const val NANOS_PER_MILLIS: Double = 1000000.0
            private const val NOT_DRAWING: Boolean = false
            private const val RENDER_NOT_REQUESTED: Boolean = false
            private const val RENDER_REQUESTED: Boolean = true
        }
    }

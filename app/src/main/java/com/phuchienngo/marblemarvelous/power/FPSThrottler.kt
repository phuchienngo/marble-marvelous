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
        private val isDrawing = AtomicBoolean(false)
        private val isContinuousRendering = AtomicBoolean(true)
        private val requestRender = AtomicBoolean(false)

        @Volatile
        private var frameTimeMs = 16.666666666666668

        private var mRenderThread: FpsThrottlerThread? = null

        fun resume() {
            Gdx.app.graphics.isContinuousRendering = false
            requestRender.set(false)
            isDrawing.set(false)
            updateFrameTime()
            mRenderThread = FpsThrottlerThread()
            mRenderThread!!.start()
        }

        fun pause() {
            Gdx.app.graphics.isContinuousRendering = true
            mRenderThread?.let { thread ->
                val handler = thread.getHandler()
                if (handler != null) {
                    handler.sendEmptyMessage(0)
                } else {
                    thread.interrupt()
                }
                mRenderThread = null
            }
            requestRendering()
        }

        fun dispose() {
            pause()
        }

        fun beginFrame() {
            isDrawing.set(true)
        }

        fun endFrame(fps: Int) {
            this.fps =
                if (fps <= 0) {
                    1
                } else if (isPowerSaveMode) {
                    fps / 2
                } else {
                    fps
                }
            updateFrameTime()
            isDrawing.set(false)
        }

        private fun updateFrameTime() {
            frameTimeMs = 1000.0 / (if (isContinuousRendering.get()) fps else 60).toDouble()
        }

        fun setPowerSaveMode(isPowerSaveMode: Boolean) {
            this.isPowerSaveMode = isPowerSaveMode
        }

        fun setContinuousRendering(continuousRendering: Boolean) {
            isContinuousRendering.set(continuousRendering)
        }

        fun requestRendering(force: Boolean) {
            if (!isContinuousRendering.get()) {
                requestRender.set(true)
            }
            if (force) {
                requestRendering()
            }
        }

        private fun requestRendering() {
            ((Gdx.app.graphics as AndroidGraphics).view as GLSurfaceView).queueEvent {
                Gdx.app.graphics.requestRendering()
            }
        }

        private class FpsHandler(
            looper: Looper,
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
                if (lastFrameTimeNanos != -1L) {
                    if (!isDrawing.get()) {
                        val dms = (frameTimeNanos - lastFrameTimeNanos) / 1000000.0
                        if (dms >= frameTimeMs) {
                            if (isContinuousRendering.get() ||
                                (!isContinuousRendering.get() && requestRender.get())
                            ) {
                                requestRendering()
                                requestRender.compareAndSet(true, false)
                            }
                            lastFrameTimeNanos = frameTimeNanos
                        }
                    }
                } else {
                    lastFrameTimeNanos = frameTimeNanos
                    requestRendering()
                }
                Choreographer.getInstance().postFrameCallback(this)
            }
        }

        companion object {
            private const val TAG = "FPSThrottler"
        }
    }

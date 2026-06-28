package com.phuchienngo.marblemarvelous.wallpaper

import android.os.SystemClock
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.phuchienngo.marblemarvelous.input.InputMultiplexer
import com.phuchienngo.marblemarvelous.power.FPSThrottler
import com.phuchienngo.marblemarvelous.utils.BaseMathUtils
import com.phuchienngo.marblemarvelous.utils.Console
import com.phuchienngo.marblemarvelous.utils.Size
import com.phuchienngo.marblemarvelous.wallpaper.controller.PageSwipeController
import com.phuchienngo.marblemarvelous.wallpaper.controller.ScreenRotationController
import com.phuchienngo.marblemarvelous.wallpaper.controller.UserPresenceController
import java.util.concurrent.atomic.AtomicBoolean

open class BaseWallpaperEngine(
    inputMultiplexer: InputMultiplexer,
    private val fpsThrottler: FPSThrottler
) : UserAwareWallpaperService.UserAwareEngine(inputMultiplexer) {
    @JvmField protected var app: Application? = null

    @JvmField protected var screenSize: Size? = null

    @JvmField protected var isPaused = false

    @Volatile
    @JvmField
    protected var initialized = false

    @JvmField protected var screenRotation = 0.0f

    @JvmField protected var isPreview = false

    @JvmField protected var isPreviewFirst = false
    private var hasBeenPreviewed = false
    private var hasBeenSet = false
    private val wallpaperLoaded: AtomicBoolean = AtomicBoolean(WALLPAPER_NOT_LOADED)
    private var continuousRendering = true
    private var continuousRenderingTarget = true
    private var requestedRendering = false
    private var lastTimeNanos = 0L

    @JvmField protected var pageSwipeController = PageSwipeController()

    @Synchronized
    override fun create() {
        setApplication()
        val pid: Int = android.os.Process.myPid()
        Console.log("Pixel 2018 Wallpapers", "PID:", pid.toString())
        fpsThrottler.setPowerSaveMode(isPowerSave())
        screenSize = Size(app!!.graphics.width.toFloat(), app!!.graphics.height.toFloat())
        initialized = true
        initialize()
        Gdx.gl.glEnable(UserAwareWallpaperService.GL_BINNING_CONTROL_HINT_QCOM)
        Gdx.gl.glHint(UserAwareWallpaperService.GL_BINNING_CONTROL_HINT_QCOM, UserAwareWallpaperService.GL_BINNING_QCOM)
        Console.log(TAG, "Create")
        resume()
    }

    override fun resize(
        width: Int,
        height: Int
    ) {
        screenSize!!.setWidth(width.toFloat())
        screenSize!!.setHeight(height.toFloat())
        resetDeltaTime()
        requestRendering(force = false)
    }

    @Synchronized
    override fun render() {
        if (initialized) {
            val currentTimeNanos: Long = SystemClock.elapsedRealtimeNanos()
            val delta: Float = BaseMathUtils.clamp((currentTimeNanos - lastTimeNanos) / 1.0E9f, 0.0f, 1.0f)
            fpsThrottler.beginFrame()
            val wasLoaded: Boolean = isLoaded()
            pageSwipeController.update(delta)
            updateWallpaper(delta)
            if (!wasLoaded) {
                fpsThrottler.endFrame(60)
                lastTimeNanos =
                    if (isLoaded()) {
                        SystemClock.elapsedRealtimeNanos()
                    } else {
                        currentTimeNanos
                    }
                return
            }
            val fps: Int = renderWallpaper()
            fpsThrottler.endFrame(fps)
            lastTimeNanos = currentTimeNanos
            if (!continuousRendering && requestedRendering) {
                fpsThrottler.requestRendering(force = false)
                requestedRendering = isCharging()
            }
            if (continuousRendering != continuousRenderingTarget) {
                continuousRendering = continuousRenderingTarget
                fpsThrottler.setContinuousRendering(continuousRendering)
                requestRendering(force = false)
            }
        }
    }

    @Synchronized
    override fun pause() {
        fpsThrottler.pause()
        isPaused = true
    }

    @Synchronized
    override fun resume() {
        fpsThrottler.resume()
        isPaused = false
        resetDeltaTime()
        requestRendering(force = false)
    }

    @Synchronized
    override fun dispose() {
        fpsThrottler.dispose()
        initialized = false
        if (!wallpaperLoaded.get()) {
            setWallpaperReady()
        }
    }

    fun isScrollAnimating(): Boolean = pageSwipeController.isScrollAnimating()

    override fun powerSaveChange(isPowerSave: Boolean) {
        fpsThrottler.setPowerSaveMode(isPowerSave())
        resetDeltaTime()
        requestRendering(force = false)
    }

    override fun chargingStateChange(isCharging: Boolean) {
        resetDeltaTime()
        requestRendering(force = false)
    }

    override fun isLoaded(): Boolean = wallpaperLoaded.get()

    override fun iconDropped(
        x: Int,
        y: Int
    ) {}

    override fun offsetChange(
        xOffset: Float,
        yOffset: Float,
        xOffsetStep: Float,
        yOffsetStep: Float,
        xPixelOffset: Int,
        yPixelOffset: Int
    ) {
        if (!isLoaded() || xOffsetStep == 0.0f || xOffsetStep == -1.0f) {
            return
        }
        pageSwipeController.setPageSwipe(xOffset, xOffsetStep)
        requestRendering(force = false)
        scrollChange(pageSwipeController.getPageOffsetRaw())
    }

    override fun userPresenceChange(
        newUserPresence: String,
        prevUserPresence: String,
        animate: Boolean
    ) {
        val continuousRenderingTmp: Boolean =
            !(
                newUserPresence == UserPresenceController.PRESENCE_AOD ||
                    newUserPresence == UserPresenceController.PRESENCE_OFF
            )
        if (continuousRenderingTarget != continuousRenderingTmp) {
            lastTimeNanos = SystemClock.elapsedRealtimeNanos()
            continuousRenderingTarget = continuousRenderingTmp
            requestRendering(force = true)
        }
    }

    override fun screenOrientationChange(orientation: ScreenRotationController.ScreenRotation) {
        screenRotation =
            if (orientation == ScreenRotationController.ScreenRotation.LANDSCAPE) {
                Math.toRadians(90.0).toFloat()
            } else {
                if (orientation == ScreenRotationController.ScreenRotation.INV_LANDSCAPE) {
                    Math.toRadians(-90.0).toFloat()
                } else {
                    0.0f
                }
            }
        resetDeltaTime()
        requestRendering(force = true)
    }

    override fun previewStateChange(isPreview: Boolean) {
        this.isPreview = isPreview
        hasBeenSet = hasBeenSet or (hasBeenPreviewed && !isPreview)
        hasBeenPreviewed = hasBeenPreviewed or isPreview
        isPreviewFirst = hasBeenPreviewed && !hasBeenSet
        requestRendering(force = true)
    }

    protected fun setWallpaperReady() {
        wallpaperLoaded.set(WALLPAPER_LOADED)
    }

    protected open fun initialize() {}

    protected open fun updateWallpaper(delta: Float) {}

    protected open fun renderWallpaper(): Int = 60

    protected open fun scrollChange(scrollX: Float) {}

    protected fun holdWakelockAOD(isAODAnimating: Boolean) {
        if ((
                getUserPresence() == UserPresenceController.PRESENCE_AOD ||
                    getUserPresence() == UserPresenceController.PRESENCE_OFF
            ) && isAODAnimating
        ) {
            requestRendering(force = false)
        }
    }

    protected fun requestRendering(force: Boolean) {
        if (!continuousRendering || force) {
            fpsThrottler.requestRendering(force)
            if (!continuousRendering) {
                requestedRendering = true
            }
        }
    }

    private fun setApplication() {
        if (app == null) {
            app = Gdx.app
        }
    }

    private fun resetDeltaTime() {
        val currentTimeMillis: Long = SystemClock.elapsedRealtimeNanos()
        val delta: Float = (currentTimeMillis - lastTimeNanos) / 1.0E9f
        if (delta > 0.2f) {
            lastTimeNanos = currentTimeMillis
        }
    }

    companion object {
        private val TAG: String = BaseWallpaperEngine::class.java.toString()
        private const val WALLPAPER_LOADED: Boolean = true
        private const val WALLPAPER_NOT_LOADED: Boolean = false
    }
}

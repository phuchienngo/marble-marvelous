package com.phuchienngo.marblemarvelous.wallpaper

import android.app.WallpaperColors
import android.opengl.GLSurfaceView
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidGraphics
import com.badlogic.gdx.backends.android.AndroidLiveWallpaperService
import com.badlogic.gdx.backends.android.AndroidWallpaperListener
import com.phuchienngo.marblemarvelous.BuildConfig
import com.phuchienngo.marblemarvelous.di.DaggerWallpaperComponent
import com.phuchienngo.marblemarvelous.input.InputMultiplexer
import com.phuchienngo.marblemarvelous.input.InputProcessor
import com.phuchienngo.marblemarvelous.utils.Console
import com.phuchienngo.marblemarvelous.wallpaper.WallpaperColorProcessor
import com.phuchienngo.marblemarvelous.wallpaper.controller.ChargingController
import com.phuchienngo.marblemarvelous.wallpaper.controller.PowerSaveController
import com.phuchienngo.marblemarvelous.wallpaper.controller.ScreenRotationController
import com.phuchienngo.marblemarvelous.wallpaper.controller.TouchController
import com.phuchienngo.marblemarvelous.wallpaper.controller.UserPresenceController
import com.phuchienngo.marblemarvelous.wallpaper.listener.ChargingListener
import com.phuchienngo.marblemarvelous.wallpaper.listener.PowerSaveListener
import com.phuchienngo.marblemarvelous.wallpaper.listener.ScreenOrientationListener
import com.phuchienngo.marblemarvelous.wallpaper.listener.TouchListener
import com.phuchienngo.marblemarvelous.wallpaper.listener.UserPresenceListener
import java.util.concurrent.atomic.AtomicBoolean

abstract class UserAwareWallpaperService :
    AndroidLiveWallpaperService(),
    PowerSaveListener,
    UserPresenceListener,
    ScreenOrientationListener,
    ChargingListener,
    TouchListener {
    private var chargingController: ChargingController? = null

    @JvmField protected var engine: UserAwareEngine? = null
    private var powerSaveController: PowerSaveController? = null
    private var receiverRegistered = false
    private var screenRotationController: ScreenRotationController? = null
    private var touchController: TouchController? = null
    private var userPresenceController: UserPresenceController? = null

    abstract fun createEngine(): UserAwareEngine

    open fun onCreateAppConfig(): AndroidApplicationConfiguration =
        AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            numSamples = 2
            r = 8
            g = 8
            b = 8
            a = 8
            depth = 16
        }

    override fun onCreateApplication() {
        super.onCreateApplication()
        app.logLevel = 1
        val engine =
            createEngine().also {
                this.engine = it
                it.updateAndroidWallpaperEngineReference(linkedEngine)
            }
        initialize(engine, onCreateAppConfig())
        val component =
            DaggerWallpaperComponent
                .factory()
                .create(this, this, this, this, this, this)
        userPresenceController = component.userPresenceController().also { it.resume(true) }
        powerSaveController = component.powerSaveController().also { it.resume(true) }
        screenRotationController = component.screenRotationController().also { it.resume(true) }
        chargingController = component.chargingController().also { it.resume(true) }
        touchController = component.touchController()
        receiverRegistered = true
    }

    private fun glView(): GLSurfaceView = (app.graphics as AndroidGraphics).view as GLSurfaceView

    override fun onUserPresenceChanged(
        userPresence: String,
        animate: Boolean,
    ) {
        glView().queueEvent { engine?.updateUserPresence(userPresence, animate) }
    }

    override fun onPowerSaveModeChanged(isPowerSaveMode: Boolean) {
        glView().queueEvent { engine?.updatePowerMode(isPowerSaveMode) }
    }

    override fun onWindowOrientationChanged(orientation: ScreenRotationController.ScreenRotation) {
        glView().queueEvent { engine?.updateScreenOrientation(orientation) }
    }

    override fun onChargingStateChanged(isCharging: Boolean) {
        glView().queueEvent { engine?.updateIsCharging(isCharging) }
    }

    override fun onTouchProcessed(
        screenX: Int,
        screenY: Int,
        index: Int,
        type: TouchController.TouchType,
    ) {
        glView().queueEvent { engine?.processTouchEvent(screenX, screenY, index, type) }
    }

    fun requestRendering() {
        glView().queueEvent { app.graphics.requestRendering() }
    }

    override fun onCreateEngine(): WallpaperService.Engine =
        object : AndroidLiveWallpaperService.AndroidWallpaperEngine() {
            private val wallpaperInitialized = AtomicBoolean(false)

            override fun onComputeColors(): WallpaperColors? = super.onComputeColors()

            override fun onCreate(surfaceHolder: SurfaceHolder) {
                super.onCreate(surfaceHolder)
                setTouchEventsEnabled(true)
                engine?.updateAndroidWallpaperEngineReference(this)
            }

            override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
                if (wallpaperInitialized.compareAndSet(false, true) && engine?.isLoaded() == false) {
                    requestRendering()
                }
                super.onSurfaceRedrawNeeded(holder)
            }

            override fun onTouchEvent(event: MotionEvent) {
                touchController?.processTouchEvent(event)
            }

            // NOTE: WallpaperService.Engine.onAmbientModeChanged is not overridable via
            // the libGDX engine here (as in the original); kept as a plain method.
            fun onAmbientModeChanged(
                inAmbientMode: Boolean,
                animated: Boolean,
            ) {
                userPresenceController?.updateAmbientMode(inAmbientMode, animated)
            }

            override fun onDestroy() {
                if (engines == 0) {
                    engine?.let {
                        it.dispose()
                        engine = null
                    }
                }
                super.onDestroy()
            }

            override fun onPause() {
                super.onPause()
                engine?.pause()
            }
        }

    override fun onDestroy() {
        if (receiverRegistered) {
            userPresenceController?.dispose()
            powerSaveController?.dispose()
            screenRotationController?.dispose()
            chargingController?.dispose()
            receiverRegistered = false
            userPresenceController = null
            powerSaveController = null
            screenRotationController = null
            chargingController = null
            touchController = null
        }
        engine?.let {
            it.dispose()
            engine = null
        }
        super.onDestroy()
    }

    abstract class UserAwareEngine(
        private val multiplexer: InputMultiplexer,
    ) : AndroidWallpaperListener,
        ApplicationListener {
        private var engineReference: AndroidLiveWallpaperService.AndroidWallpaperEngine? = null

        @Volatile
        private var isPowerSave = false
        private var userPresence = "unlocked"
        private var chargingState = false
        private var screenOrientation = ScreenRotationController.DEFAULT_SCREEN_ROTATION

        @JvmField protected var wallpaperColors: WallpaperColors? = null

        abstract fun chargingStateChange(isCharging: Boolean)

        abstract fun isLoaded(): Boolean

        abstract fun powerSaveChange(isPowerSave: Boolean)

        abstract fun screenOrientationChange(orientation: ScreenRotationController.ScreenRotation)

        abstract fun userPresenceChange(
            newUserPresence: String,
            prevUserPresence: String,
            animate: Boolean,
        )

        protected fun isCharging(): Boolean = chargingState

        protected fun isPowerSave(): Boolean = isPowerSave

        protected fun getUserPresence(): String = userPresence

        protected fun getScreenOrientation(): ScreenRotationController.ScreenRotation = screenOrientation

        protected fun notifyWallpaperColorsChanged() {
            Console.log(TAG, "//// UI Color Change Notified?", (engineReference != null).toString())
            engineReference?.notifyColorsChanged()
        }

        protected fun setWallpaperColors(wallpaperColors: WallpaperColors?) {
            this.wallpaperColors = wallpaperColors
        }

        protected fun setInputProcessor(inputProcessor: InputProcessor) {
            multiplexer.addProcessor(inputProcessor)
        }

        protected fun removeInputProcessor(inputProcessor: InputProcessor) {
            multiplexer.removeProcessor(inputProcessor)
        }

        fun processTouchEvent(
            screenX: Int,
            screenY: Int,
            index: Int,
            type: TouchController.TouchType,
        ) {
            when (type) {
                TouchController.TouchType.DOWN -> multiplexer.touchDown(screenX, screenY, index)
                TouchController.TouchType.UP -> multiplexer.touchUp(screenX, screenY, index)
                TouchController.TouchType.MOVE -> multiplexer.touchDragged(screenX, screenY, index)
            }
        }

        fun updatePowerMode(isPowerSave: Boolean) {
            this.isPowerSave = BuildConfig.ENABLE_FPS_POWERSAVE && isPowerSave
            powerSaveChange(this.isPowerSave)
        }

        fun updateUserPresence(
            userPresence: String,
            animate: Boolean,
        ) {
            val oldUserPresence = this.userPresence
            this.userPresence = userPresence
            val isKnownPresence =
                userPresence == UserPresenceController.PRESENCE_OFF ||
                    userPresence == UserPresenceController.PRESENCE_AOD ||
                    userPresence == UserPresenceController.PRESENCE_LOCKED
            val animateAdjusted = if (isKnownPresence) !isPowerSave() && animate else animate
            userPresenceChange(userPresence, oldUserPresence, animateAdjusted)
        }

        fun updateScreenOrientation(orientation: ScreenRotationController.ScreenRotation) {
            screenOrientation = orientation
            screenOrientationChange(orientation)
        }

        fun updateIsCharging(isCharging: Boolean) {
            chargingState = isCharging
            chargingStateChange(isCharging)
        }

        fun computeWallpaperColors(androidWallpaperEngine: AndroidLiveWallpaperService.AndroidWallpaperEngine): WallpaperColors? {
            val fallback = wallpaperColors
            val colorProcessor = this as? WallpaperColorProcessor
            if (colorProcessor == null && fallback == null) return null
            val primary = colorProcessor?.mainWallpaperColor() ?: fallback?.primaryColor ?: return null
            val secondary = colorProcessor?.secondaryWallpaperColor() ?: fallback?.secondaryColor
            val tertiary = colorProcessor?.tertiaryWallpaperColor() ?: fallback?.tertiaryColor
            return WallpaperColors(primary, secondary, tertiary)
        }

        fun updateAndroidWallpaperEngineReference(engineReference: AndroidLiveWallpaperService.AndroidWallpaperEngine?) {
            this.engineReference = engineReference
        }
    }

    companion object {
        const val GL_BINNING_CONTROL_HINT_QCOM = 36784
        const val GL_BINNING_QCOM = 36785
        const val TAG = "WP"
    }
}

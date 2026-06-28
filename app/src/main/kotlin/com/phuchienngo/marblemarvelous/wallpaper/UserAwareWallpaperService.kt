package com.phuchienngo.marblemarvelous.wallpaper

import android.app.WallpaperColors
import android.graphics.Color
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
import com.phuchienngo.marblemarvelous.di.WallpaperComponent
import com.phuchienngo.marblemarvelous.input.InputMultiplexer
import com.phuchienngo.marblemarvelous.input.InputProcessor
import com.phuchienngo.marblemarvelous.utils.Console
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

    open fun onCreateAppConfig(): AndroidApplicationConfiguration {
        val config: AndroidApplicationConfiguration = AndroidApplicationConfiguration()
        config.useAccelerometer = false
        config.useCompass = false
        config.useGyroscope = false
        config.numSamples = 2
        config.r = 8
        config.g = 8
        config.b = 8
        config.a = 8
        config.depth = 16
        return config
    }

    override fun onCreateApplication() {
        super.onCreateApplication()
        app.logLevel = 1
        val engine: UserAwareEngine = createEngine()
        this.engine = engine
        engine.updateAndroidWallpaperEngineReference(linkedEngine)
        initialize(engine, onCreateAppConfig())
        val component: WallpaperComponent =
            DaggerWallpaperComponent
                .factory()
                .create(this, this, this, this, this, this)
        userPresenceController = component.userPresenceController()
        userPresenceController?.resume(fireStraightAway = true)
        powerSaveController = component.powerSaveController()
        powerSaveController?.resume(fireStraightAway = true)
        screenRotationController = component.screenRotationController()
        screenRotationController?.resume(fireStraightAway = true)
        chargingController = component.chargingController()
        chargingController?.resume(fireStraightAway = true)
        touchController = component.touchController()
        receiverRegistered = true
    }

    private fun glView(): GLSurfaceView = (app.graphics as AndroidGraphics).view as GLSurfaceView

    override fun onUserPresenceChanged(
        userPresence: String,
        animate: Boolean
    ) {
        glView().queueEvent updateUserPresence@{
            engine?.updateUserPresence(userPresence, animate)
            return@updateUserPresence
        }
    }

    override fun onPowerSaveModeChanged(isPowerSaveMode: Boolean) {
        glView().queueEvent updatePowerMode@{
            engine?.updatePowerMode(isPowerSaveMode)
            return@updatePowerMode
        }
    }

    override fun onWindowOrientationChanged(orientation: ScreenRotationController.ScreenRotation) {
        glView().queueEvent updateScreenOrientation@{
            engine?.updateScreenOrientation(orientation)
            return@updateScreenOrientation
        }
    }

    override fun onChargingStateChanged(isCharging: Boolean) {
        glView().queueEvent updateChargingState@{
            engine?.updateIsCharging(isCharging)
            return@updateChargingState
        }
    }

    override fun onTouchProcessed(
        screenX: Int,
        screenY: Int,
        index: Int,
        type: TouchController.TouchType
    ) {
        glView().queueEvent processTouchEvent@{
            engine?.processTouchEvent(screenX, screenY, index, type)
            return@processTouchEvent
        }
    }

    fun requestRendering() {
        glView().queueEvent requestRender@{
            return@requestRender app.graphics.requestRendering()
        }
    }

    override fun onCreateEngine(): WallpaperService.Engine =
        object : AndroidLiveWallpaperService.AndroidWallpaperEngine() {
            private val wallpaperInitialized: AtomicBoolean = AtomicBoolean(WALLPAPER_NOT_INITIALIZED)

            override fun onComputeColors(): WallpaperColors? = super.onComputeColors()

            override fun onCreate(surfaceHolder: SurfaceHolder) {
                super.onCreate(surfaceHolder)
                setTouchEventsEnabled(TOUCH_EVENTS_ENABLED)
                engine?.updateAndroidWallpaperEngineReference(this)
            }

            override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
                if (wallpaperInitialized.compareAndSet(WALLPAPER_NOT_INITIALIZED, WALLPAPER_INITIALIZED) &&
                    engine?.isLoaded() == false
                ) {
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
                animated: Boolean
            ) {
                userPresenceController?.updateAmbientMode(inAmbientMode, animated)
            }

            override fun onDestroy() {
                if (engines == 0) {
                    val currentEngine: UserAwareEngine? = engine
                    if (currentEngine != null) {
                        currentEngine.dispose()
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
        val currentEngine: UserAwareEngine? = engine
        if (currentEngine != null) {
            currentEngine.dispose()
            engine = null
        }
        super.onDestroy()
    }

    abstract class UserAwareEngine(
        private val multiplexer: InputMultiplexer
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
            animate: Boolean
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
            type: TouchController.TouchType
        ) {
            if (type == TouchController.TouchType.DOWN) {
                multiplexer.touchDown(screenX, screenY, index)
            } else {
                if (type == TouchController.TouchType.UP) {
                    multiplexer.touchUp(screenX, screenY, index)
                } else {
                    multiplexer.touchDragged(screenX, screenY, index)
                }
            }
        }

        fun updatePowerMode(isPowerSave: Boolean) {
            this.isPowerSave = BuildConfig.ENABLE_FPS_POWERSAVE && isPowerSave
            powerSaveChange(this.isPowerSave)
        }

        fun updateUserPresence(
            userPresence: String,
            animate: Boolean
        ) {
            val oldUserPresence: String = this.userPresence
            this.userPresence = userPresence
            val isKnownPresence: Boolean =
                userPresence == UserPresenceController.PRESENCE_OFF ||
                    userPresence == UserPresenceController.PRESENCE_AOD ||
                    userPresence == UserPresenceController.PRESENCE_LOCKED
            val animateAdjusted: Boolean =
                if (isKnownPresence) {
                    !isPowerSave() && animate
                } else {
                    animate
                }
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
            val fallback: WallpaperColors? = wallpaperColors
            val colorProcessor: WallpaperColorProcessor? = this as? WallpaperColorProcessor
            if (colorProcessor == null && fallback == null) {
                return null
            }
            val primary: Color = colorProcessor?.mainWallpaperColor() ?: fallback?.primaryColor ?: return null
            val secondary: Color? = colorProcessor?.secondaryWallpaperColor() ?: fallback?.secondaryColor
            val tertiary: Color? = colorProcessor?.tertiaryWallpaperColor() ?: fallback?.tertiaryColor
            return WallpaperColors(primary, secondary, tertiary)
        }

        fun updateAndroidWallpaperEngineReference(engineReference: AndroidLiveWallpaperService.AndroidWallpaperEngine?) {
            this.engineReference = engineReference
        }
    }

    companion object {
        const val GL_BINNING_CONTROL_HINT_QCOM: Int = 36784
        const val GL_BINNING_QCOM: Int = 36785
        const val TAG: String = "WP"
        private const val TOUCH_EVENTS_ENABLED: Boolean = true
        private const val WALLPAPER_INITIALIZED: Boolean = true
        private const val WALLPAPER_NOT_INITIALIZED: Boolean = false
    }
}

package com.phuchienngo.marblemarvelous.celestialBodies

import android.os.SystemClock
import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Material
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.g3d.utils.DefaultTextureBinder
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.graphics.g3d.utils.RenderContext
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.phuchienngo.marblemarvelous.animations.TweenController
import com.phuchienngo.marblemarvelous.celestialBodies.core.Glow
import com.phuchienngo.marblemarvelous.celestialBodies.core.Stars
import com.phuchienngo.marblemarvelous.celestialBodies.shaders.PlanetMask
import com.phuchienngo.marblemarvelous.input.InputMultiplexer
import com.phuchienngo.marblemarvelous.interfaces.WallpaperThemeProcessor
import com.phuchienngo.marblemarvelous.power.FPSThrottler
import com.phuchienngo.marblemarvelous.utils.Console
import com.phuchienngo.marblemarvelous.utils.FrustumUtils
import com.phuchienngo.marblemarvelous.utils.ShaderUtils
import com.phuchienngo.marblemarvelous.view.BaseWallpaperEngine
import com.phuchienngo.marblemarvelous.view.controller.UserPresenceController
import kotlin.math.tan

abstract class PlanetEngine protected constructor(
    app: Application,
    inputMultiplexer: InputMultiplexer,
    fpsThrottler: FPSThrottler,
) : BaseWallpaperEngine(inputMultiplexer, fpsThrottler),
    WallpaperThemeProcessor {
    private val mApp: Application = app
    private var batch: SpriteBatch? = null
    private var camera: OrthographicCamera? = null
    private var glow: Glow? = null

    @Volatile
    private var isLoading = false
    private var lastTimeUpdated: Long = 0
    private var mAssetManager: AssetManager? = null
    private var mCamera: PerspectiveCamera? = null
    private var mEnvironment: Environment? = null
    private var mPlanet: Renderable? = null
    private var mRenderContext: RenderContext? = null
    private var mShader: Shader? = null
    private var mSunLight: PointLight? = null
    private var model: Model? = null
    private var mask: PlanetMask? = null

    @JvmField protected var stars: Stars? = null

    @JvmField protected var timeScale = 0.0f
    private var tintGlow: ShaderProgram? = null
    private var prevCameraPositionIndex = -1

    @JvmField protected var animate = true
    private var glowColor = Color(Color.WHITE)

    @JvmField protected val tweenAod = TweenController()

    @JvmField protected val tweenRotation = TweenController()

    @JvmField protected val tweenZoom = TweenController()

    protected abstract fun animate(
        f: Float,
        renderable: Renderable,
        pointLight: PointLight,
        perspectiveCamera: PerspectiveCamera,
        f2: Float,
    )

    protected abstract fun applyAssets(
        renderable: Renderable,
        assetManager: AssetManager,
    )

    protected abstract fun createShader(): Shader

    protected abstract fun getPreviewEasing(): TweenController.Easing

    protected abstract fun getPreviewTiming(): Float

    protected abstract fun loadAssets(assetManager: AssetManager)

    init {
        tweenAod.set(0.0f)
        tweenZoom.set(0.0f)
        tweenRotation.set(0.0f)
        pageSwipeController.setMinPagesToSwipe(4)
        pageSwipeController.setPageSwipeDamping(FOV_INCREASE)
        lastTimeUpdated = SystemClock.elapsedRealtime() - 10000
    }

    override fun initialize() {
        super.initialize()
        mAssetManager = AssetManager()
        loadAssets(mAssetManager!!)
        isLoading = true
        mSunLight = PointLight()
        mSunLight!!.set(Color.WHITE, Vector3(INITIAL_LIGHT_POSITION), FOV_INCREASE)
        mEnvironment = Environment()
        mEnvironment!!.add(mSunLight)
        val modelBuilder = ModelBuilder()
        model = modelBuilder.createSphere(2.0f, 2.0f, 2.0f, 60, 60, Material(), 25L)
        mRenderContext = RenderContext(DefaultTextureBinder(1, 1))
        mShader = createShader()
        mShader!!.init()
        val currentModel: Model =
            requireNotNull(model) {
                "Planet model must be created before renderable setup."
            }
        val blockPart =
            currentModel
                .nodes
                .get(0)
                .parts
                .get(0)
        mPlanet = Renderable()
        blockPart.setRenderable(mPlanet)
        mPlanet!!.environment = mEnvironment
        mPlanet!!.worldTransform.idt()
        stars = Stars(mCamera)
        batch = SpriteBatch()
        camera = OrthographicCamera()
        mask = PlanetMask()
        glow = Glow()
        tintGlow = ShaderUtils.load("celestialbodies/tintGlow")
        camera!!.setToOrtho(true, mApp.graphics.width.toFloat(), mApp.graphics.height.toFloat())
        batch!!.projectionMatrix = camera!!.combined
        batch!!.shader = tintGlow
        glowColor = getGlowColor()
        tintGlow!!.bind()
        tintGlow!!.setUniformf("u_tint_color", glowColor)
        tintGlow!!.setUniformf("u_resolution", mApp.graphics.width.toFloat(), mApp.graphics.height.toFloat())
        setWallpaperReady()
    }

    override fun resize(
        width: Int,
        height: Int,
    ) {
        if (width.toFloat() != screenSize!!.getWidth() || height.toFloat() != screenSize!!.getHeight()) {
            super.resize(width, height)
            if (camera != null) updateSceneAndCamera(true)
            tintGlow!!.bind()
            tintGlow!!.setUniformf("u_resolution", width.toFloat(), height.toFloat())
        }
    }

    override fun resume() {
        super.resume()
        if (camera != null) updateSceneAndCamera(false)
    }

    override fun pause() {
        super.pause()
        lastTimeUpdated = SystemClock.elapsedRealtime()
    }

    override fun dispose() {
        super.dispose()
        mAssetManager?.let {
            it.dispose()
            mAssetManager = null
        }
        stars?.let {
            it.dispose()
            stars = null
        }
        mShader?.let {
            it.dispose()
            mShader = null
        }
        batch?.dispose()
        batch = null
        model?.dispose()
        model = null
        mask?.dispose()
        mask = null
        glow?.dispose()
        glow = null
        tintGlow?.dispose()
        tintGlow = null
        mPlanet = null
        mEnvironment = null
        mSunLight = null
    }

    override fun updateWallpaper(delta: Float) {
        super.updateWallpaper(delta)
        timeScale = delta / 0.017f
        if (isLoading && mAssetManager!!.update() && mAssetManager!!.progress.toDouble() == 1.0) {
            doneLoading()
            animateWakeUp(getUserPresence(), true)
        }
        if (isLoading) return
        tweenRotation.update(delta)
        tweenAod.update(delta)
        tweenZoom.update(delta)
        stars!!.transform.rotation.set(Vector3.Y, -pageSwipeController.getPageOffset() * STARS_MOTION_INTENSITY)
        animate(delta, mPlanet!!, mSunLight!!, mCamera!!, pageSwipeController.getPageOffset())
        mCamera!!.update()
        mSunLight!!.setIntensity(FOV_INCREASE - tweenAod.getValue())
        stars!!.setAOD(tweenAod.getValue())
        mask!!.setAOD(tweenAod.getValue())
        mask!!.setFieldOfViewDifference(FOV_INCREASE)
        val z = tweenAod.isAnimating() || tweenZoom.isAnimating() || tweenRotation.isAnimating()
        holdWakelockAOD(z)
    }

    override fun renderWallpaper(): Int {
        super.renderWallpaper()
        if (isLoading) return 60
        Gdx.gl.glClear(16640)
        mRenderContext!!.begin()
        mask!!.begin(mCamera!!, mRenderContext)
        mask!!.render(mPlanet!!)
        mask!!.end()
        mRenderContext!!.end()
        val glowTexture = glow!!.generateGlow(mask!!.getFboTexture(), blurTimes())
        stars!!.begin(mCamera!!)
        stars!!.render()
        stars!!.end()
        val diff =
            (
                tan(((mCamera!!.fieldOfView + FOV_INCREASE) * 0.017453292f).toDouble()) /
                    tan((mCamera!!.fieldOfView * 0.017453292f).toDouble())
            ).toFloat()
        val dW = screenSize!!.getWidth() * (diff - FOV_INCREASE)
        val dH = (diff - FOV_INCREASE) * screenSize!!.getHeight()
        batch!!.begin()
        batch!!.enableBlending()
        tintGlow!!.setUniformf("u_tint_color", glowColor)
        batch!!.draw(glowTexture, -dW / 2.0f, -dH / 2.0f, screenSize!!.getWidth() + dW, screenSize!!.getHeight() + dH)
        batch!!.end()
        mRenderContext!!.begin()
        mShader!!.begin(mCamera!!, mRenderContext)
        mShader!!.render(mPlanet!!)
        mRenderContext!!.end()
        val requiresHighFPS = tweenRotation.isAnimating() || tweenZoom.isAnimating() || tweenAod.isAnimating()
        val requiresMediumFPS = pageSwipeController.isScrollAnimating()
        return if (requiresHighFPS) {
            60
        } else if (requiresMediumFPS) {
            30
        } else {
            18
        }
    }

    private fun doneLoading() {
        isLoading = false
        applyAssets(mPlanet!!, mAssetManager!!)
        resetScene()
    }

    @Synchronized
    protected open fun resetScene() {
        setCamera()
    }

    private fun setCamera() {
        val width = screenSize!!.getWidth().toInt()
        val height = screenSize!!.getHeight().toInt()
        val minSideFov = FrustumUtils.vFovToHFov(getFOV(), 9.0f, 16.0f)
        val fovAdj = if (width < height) FrustumUtils.hFovToVFov(minSideFov, width.toFloat(), height.toFloat()) else minSideFov
        mCamera = PerspectiveCamera(fovAdj, width.toFloat(), height.toFloat())
        mCamera!!.near = 0.0f
        mCamera!!.far = 300.0f
    }

    private fun updateSceneAndCamera(force: Boolean) {
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastTimeUpdated > 1000 || force) {
            resetScene()
            glow!!.resize(screenSize!!.getWidth().toInt(), screenSize!!.getHeight().toInt())
            camera!!.setToOrtho(true, screenSize!!.getWidth(), screenSize!!.getHeight())
            batch!!.projectionMatrix = camera!!.combined
            lastTimeUpdated = currentTime
        }
    }

    protected fun updateStars(cameraPositionIndex: Int) {
        if (prevCameraPositionIndex == -1 || prevCameraPositionIndex != cameraPositionIndex) {
            stars!!.updateStarsPositions(mCamera)
        }
        prevCameraPositionIndex = cameraPositionIndex
    }

    override fun userPresenceChange(
        newUserPresence: String,
        prevUserPresence: String,
        animate: Boolean,
    ) {
        super.userPresenceChange(newUserPresence, prevUserPresence, animate)
        animateWakeUp(newUserPresence, animate)
        Console.log(TAG, "/// UserPresence: $prevUserPresence => $newUserPresence (animated: $animate)")
    }

    private fun animateWakeUp(
        presence: String,
        animate: Boolean,
    ) {
        var durationAod = 2.5f
        var duration = if (isPreview) getPreviewTiming() else 2.5f
        var easingAod = TweenController.Easing.EXPO_OUT
        val easing = if (isPreview) getPreviewEasing() else TweenController.Easing.QUART_OUT
        var targetAod = 0.0f
        var targetZoom = 0.0f
        var targetRotation = 0.0f
        var delay = 0.0f
        var animateUpdate = animate
        when (presence) {
            UserPresenceController.PRESENCE_LOCKED -> {
                targetZoom = FOV_INCREASE
            }

            UserPresenceController.PRESENCE_OFF -> {
                animateUpdate = !isPaused
                delay = if (!animateUpdate) 0.0f else 0.2f
                targetAod = FOV_INCREASE
                duration = 1.8f
                durationAod = 1.8f
                easingAod = TweenController.Easing.QUART_OUT
                targetRotation = LOCKED_ROTATION
                targetZoom = FOV_INCREASE
            }

            UserPresenceController.PRESENCE_AOD -> {
                targetAod = FOV_INCREASE
                duration = 1.8f
                durationAod = 1.8f
                easingAod = TweenController.Easing.QUART_OUT
                targetRotation = LOCKED_ROTATION
                targetZoom = FOV_INCREASE
            }
        }
        if (!animateUpdate) {
            tweenAod.set(targetAod)
            tweenRotation.set(targetRotation)
            tweenZoom.set(targetZoom)
        } else {
            tweenAod.to(targetAod, durationAod, delay, easingAod)
            tweenRotation.to(targetRotation, duration, delay, easing)
            tweenZoom.to(targetZoom, duration, delay, easing)
        }
    }

    fun getCamera(): PerspectiveCamera? = mCamera

    protected open fun getGlowColor(): Color = Color(Color.WHITE)

    protected open fun getFOV(): Float = CAMERA_FOV

    protected open fun blurTimes(): Int = 4

    override fun darkText(): Boolean = false

    override fun darkTheme(): Boolean = true

    companion object {
        private val TAG = PlanetEngine::class.java.toString()

        const val LOCKED_ROTATION = 2.0f
        const val LOCKED_ZOOM = 0.04f
        private const val CAMERA_FOV = 35.0f
        private const val FOV_INCREASE = 1.0f
        private const val STARS_MOTION_INTENSITY = 3.0f
        private val INITIAL_LIGHT_POSITION = Vector3(0.0f, 0.0f, -100.0f)
    }
}

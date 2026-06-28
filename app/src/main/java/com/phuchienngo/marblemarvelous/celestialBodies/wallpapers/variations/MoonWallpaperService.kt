package com.phuchienngo.marblemarvelous.celestialBodies.wallpapers.variations

import android.graphics.BitmapFactory
import android.util.Pair
import com.badlogic.gdx.Application
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.CubemapLoader
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Renderable
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.phuchienngo.marblemarvelous.R
import com.phuchienngo.marblemarvelous.animations.TweenController
import com.phuchienngo.marblemarvelous.celestialBodies.PlanetEngine
import com.phuchienngo.marblemarvelous.celestialBodies.shaders.MoonShader
import com.phuchienngo.marblemarvelous.celestialBodies.shaders.attributes.PlanetTextureAttribute
import com.phuchienngo.marblemarvelous.celestialBodies.wallpapers.PlanetWallpaper
import com.phuchienngo.marblemarvelous.di.DaggerEngineComponent
import com.phuchienngo.marblemarvelous.input.InputMultiplexer
import com.phuchienngo.marblemarvelous.power.FPSThrottler
import com.phuchienngo.marblemarvelous.transforms.SphericalTransform
import com.phuchienngo.marblemarvelous.utils.DateUtils
import com.phuchienngo.marblemarvelous.view.UserAwareWallpaperService
import com.phuchienngo.marblemarvelous.view.controller.UserPresenceController
import com.phuchienngo.marblemarvelous.weather.PhaseOfTheMoon
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.cos

class MoonWallpaperService : PlanetWallpaper() {
    override fun createEngine(): UserAwareWallpaperService.UserAwareEngine {
        BitmapFactory.decodeResource(applicationContext.resources, R.drawable.moon_preview_color_extractor)
        return DaggerEngineComponent
            .factory()
            .create(this, app)
            .moonEngine()
    }

    class MoonEngine
        @Inject
        constructor(
            app: Application,
            inputMultiplexer: InputMultiplexer,
            fpsThrottler: FPSThrottler,
        ) : PlanetEngine(app, inputMultiplexer, fpsThrottler) {
            private val aodLight: SphericalTransform = SphericalTransform(INITIAL_LIGHT_POSITION_SPHERICAL)
            private val aodLookAt: Vector3 = Vector3()
            private val aodLookAtComp: Vector3 = Vector3()
            protected var cameraPosition: Int = 0
            private val glowColorValue: Color = Color(0.98039216f, 0.98039216f, 0.98039216f, 0.8f)
            private val initLight: SphericalTransform = SphericalTransform(INITIAL_LIGHT_POSITION_SPHERICAL)
            private val lightComps: SphericalTransform = SphericalTransform(INITIAL_LIGHT_POSITION_SPHERICAL)
            private var mNormals: Cubemap? = null
            protected var mPhaseOfTheMoon: Float = 0.0f
            private var mTexture: Cubemap? = null
            private var time: Float = 0.0f

            override fun getGlowColor(): Color = glowColorValue

            override fun blurTimes(): Int = 4

            override fun loadAssets(assetManager: AssetManager) {
                assetManager.setLoader(Cubemap::class.java, CubemapLoader(InternalFileHandleResolver()))
                assetManager.load("moon/moon.ktx", Cubemap::class.java)
                assetManager.load("moon/normals.ktx", Cubemap::class.java)
            }

            override fun applyAssets(
                renderable: Renderable,
                assetManager: AssetManager,
            ) {
                mTexture = assetManager.get("moon/moon.ktx", Cubemap::class.java)
                mTexture!!.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                mNormals = assetManager.get("moon/normals.ktx", Cubemap::class.java)
                mNormals!!.setFilter(Texture.TextureFilter.MipMapNearestNearest, Texture.TextureFilter.Linear)
                renderable.material.set(PlanetTextureAttribute.createDay(mTexture))
                renderable.material.set(PlanetTextureAttribute.createNormals(mNormals))
            }

            override fun createShader(): Shader = MoonShader()

            override fun animate(
                f: Float,
                renderable: Renderable,
                pointLight: PointLight,
                perspectiveCamera: PerspectiveCamera,
                f2: Float,
            ) {
                glowColorValue.a = (0.8 * tweenAod.getValue()).toFloat()
                if (getUserPresence() != UserPresenceController.PRESENCE_AOD) {
                    time += timeScale * f
                }
                val phaseOfTheMoon: Float = MathUtils.clamp(mPhaseOfTheMoon, MIN_PHASE_OF_THE_MOON, MAX_PHASE_OF_THE_MOON)
                lightComps.set(initLight)
                lightComps.setPhi(lightComps.getPhiRad() + (6.2831855f - (6.2831855f * phaseOfTheMoon)))
                lightComps.update()
                lightComps.lerp(lightComps, aodLight, tweenAod.getValue())
                pointLight.position.set(lightComps.getCartesianCoordinates())
                val libration: Double = cos(6.283185307179586 * mPhaseOfTheMoon.toDouble()) * MOON_LIBRATION
                renderable.worldTransform
                    .idt()
                    .rotate(Vector3.Y, (-libration).toFloat())
                    .rotate(Vector3.X, libration.toFloat())
                renderable.worldTransform.rotate(Vector3.Y, -0.0f * time)
                perspectiveCamera.position.set(CAMERA_POSITIONS[cameraPosition].first)
                val scale: Float = perspectiveCamera.position.len()
                perspectiveCamera.position
                    .scl(
                        MathUtils.lerp((tweenZoom.getValue() * PlanetEngine.LOCKED_ZOOM) + 1.0f, 3.42f / scale, tweenAod.getValue()),
                    ).rotate(Vector3.Y, tweenRotation.getValue())
                    .rotate(Vector3.Y, 10.0f * f2)
                val position: Pair<Vector3, Vector3> = CAMERA_POSITIONS[cameraPosition]
                if (cameraPosition == 0) {
                    if (mPhaseOfTheMoon > 0.35f) {
                        position.first.x = -abs(position.first.x)
                        position.second.x = -abs(position.second.x)
                    } else {
                        position.first.x = abs(position.first.x)
                        position.second.x = abs(position.second.x)
                    }
                }
                perspectiveCamera.position.rotate(Vector3.Y, 4.0f * f2)
                val lookAt: Vector3 = CAMERA_POSITIONS[cameraPosition].second
                val scaleAodLookAt: Float =
                    if (mPhaseOfTheMoon <= 0.35f) {
                        1.0f
                    } else {
                        -1.0f
                    }
                aodLookAtComp.set(aodLookAt)
                aodLookAtComp.scl(scaleAodLookAt)
                aodLookAtComp.lerp(lookAt, 1.0f - tweenAod.getValue())
                perspectiveCamera.lookAt(aodLookAtComp)
            }

            override fun resetScene() {
                resetScene(DateUtils.getDayRatio())
            }

            protected fun resetScene(localDayRatio: Float) {
                super.resetScene()
                time = 0.0f
                cameraPosition =
                    if (localDayRatio <= 0.375f || localDayRatio >= 0.7083333f) {
                        1
                    } else {
                        0
                    }
                mPhaseOfTheMoon = PhaseOfTheMoon.getPhaseRatio().toFloat()
                val position: Vector3 = Vector3(CAMERA_POSITIONS[cameraPosition].first)
                aodLight.setFromCartesiansPosition(position.scl(-1.0f))
                position.set(CAMERA_POSITIONS[cameraPosition].first)
                aodLookAt.set(position.z, 0.0f, -position.x)
                aodLookAt.nor()
                aodLookAt.scl(1.0f)
                updateStars(cameraPosition)
            }

            @Synchronized
            override fun dispose() {
                mTexture = null
                mNormals = null
                super.dispose()
            }

            override fun previewStateChange(isPreview: Boolean) {
                super.previewStateChange(isPreview)
                if (isPreviewFirst) {
                    tweenRotation.set(PlanetEngine.LOCKED_ROTATION * 5.0f)
                    tweenZoom.set(2.0f)
                }
            }

            override fun getPreviewTiming(): Float = 4.0f

            override fun getPreviewEasing(): TweenController.Easing = TweenController.Easing.QUART_OUT

            companion object {
                private const val MAX_PHASE_OF_THE_MOON: Float = 0.915f
                private const val MIN_PHASE_OF_THE_MOON: Float = 0.085f
                private const val MOON_LIBRATION: Double = 5.0
                private val CAMERA_POSITIONS: Array<Pair<Vector3, Vector3>> =
                    arrayOf(
                        Pair(Vector3(-0.5f, 0.0f, 4.2f), Vector3(-0.5f, 0.0f, 0.0f)),
                        Pair(Vector3(0.0f, 0.0f, 6.3f), Vector3(0.0f, 0.0f, 0.0f)),
                    )
                private val INITIAL_LIGHT_POSITION: Vector3 = Vector3(0.0f, 0.0f, -100.0f)
                private val INITIAL_LIGHT_POSITION_SPHERICAL: SphericalTransform = SphericalTransform(INITIAL_LIGHT_POSITION)
            }
        }
}

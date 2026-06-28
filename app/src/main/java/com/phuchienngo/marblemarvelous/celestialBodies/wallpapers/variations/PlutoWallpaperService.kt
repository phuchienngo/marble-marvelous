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
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import com.phuchienngo.marblemarvelous.R
import com.phuchienngo.marblemarvelous.animations.TweenController
import com.phuchienngo.marblemarvelous.celestialBodies.PlanetEngine
import com.phuchienngo.marblemarvelous.celestialBodies.shaders.PlutoShader
import com.phuchienngo.marblemarvelous.celestialBodies.shaders.attributes.PlanetTextureAttribute
import com.phuchienngo.marblemarvelous.celestialBodies.wallpapers.PlanetWallpaper
import com.phuchienngo.marblemarvelous.di.DaggerEngineComponent
import com.phuchienngo.marblemarvelous.input.InputMultiplexer
import com.phuchienngo.marblemarvelous.power.FPSThrottler
import com.phuchienngo.marblemarvelous.transforms.SphericalTransform
import com.phuchienngo.marblemarvelous.utils.DateUtils
import com.phuchienngo.marblemarvelous.view.UserAwareWallpaperService
import com.phuchienngo.marblemarvelous.view.controller.UserPresenceController
import javax.inject.Inject

class PlutoWallpaperService : PlanetWallpaper() {
    override fun createEngine(): UserAwareWallpaperService.UserAwareEngine {
        BitmapFactory.decodeResource(applicationContext.resources, R.drawable.pluto_preview_color_extractor)
        return DaggerEngineComponent
            .factory()
            .create(this, app)
            .plutoEngine()
    }

    class PlutoEngine
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
            private val glowColorValue: Color = Color(0.5176471f, 0.6156863f, 0.9019608f, 0.9f)
            private val initLight: SphericalTransform = SphericalTransform(INITIAL_LIGHT_POSITION_SPHERICAL)
            private val lightComps: SphericalTransform = SphericalTransform(INITIAL_LIGHT_POSITION_SPHERICAL)
            protected var mDayRatio: Float = 0.0f
            private var mNormals: Cubemap? = null
            private var mTexture: Cubemap? = null
            private val quaternion: Quaternion = Quaternion()
            private var time: Float = 0.5f

            override fun getGlowColor(): Color = glowColorValue

            override fun getFOV(): Float = 24.0f

            override fun loadAssets(assetManager: AssetManager) {
                assetManager.setLoader(Cubemap::class.java, CubemapLoader(InternalFileHandleResolver()))
                assetManager.load("pluto/pluto.ktx", Cubemap::class.java)
                assetManager.load("pluto/normals.ktx", Cubemap::class.java)
            }

            override fun applyAssets(
                renderable: Renderable,
                assetManager: AssetManager,
            ) {
                mTexture = assetManager.get("pluto/pluto.ktx", Cubemap::class.java)
                mTexture!!.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                mNormals = assetManager.get("pluto/normals.ktx", Cubemap::class.java)
                mNormals!!.setFilter(Texture.TextureFilter.MipMapNearestNearest, Texture.TextureFilter.Linear)
                renderable.material.set(PlanetTextureAttribute.createDay(mTexture))
                renderable.material.set(PlanetTextureAttribute.createNormals(mNormals))
            }

            override fun createShader(): Shader = PlutoShader()

            override fun animate(
                f: Float,
                renderable: Renderable,
                pointLight: PointLight,
                perspectiveCamera: PerspectiveCamera,
                f2: Float,
            ) {
                if (getUserPresence() != UserPresenceController.PRESENCE_AOD) {
                    time += timeScale * f
                }
                var rotation: Float = -0.63f * time
                if (rotation < MAX_ROTATION) {
                    rotation = MAX_ROTATION
                }
                lightComps.lerp(initLight, aodLight, tweenAod.getValue())
                pointLight.position.set(lightComps.getCartesianCoordinates())
                pointLight.position.rotate(Vector3.Y, rotation * STARS_ROTATION_INTENSITY)
                renderable.worldTransform.idt()
                renderable.worldTransform.rotate(
                    Vector3.Y,
                    105.0f + (rotation * STARS_ROTATION_INTENSITY),
                )
                renderable.worldTransform.rotate(Vector3.X, 25.0f)
                quaternion.set(Vector3.Y, STARS_ROTATION_INTENSITY * rotation)
                stars!!.transform.rotation.mul(quaternion)
                perspectiveCamera.position.set(CAMERA_POSITIONS[cameraPosition].first)
                val scale: Float = perspectiveCamera.position.len()
                perspectiveCamera.position.scl(
                    MathUtils.lerp(1.0f + (tweenZoom.getValue() * LOCKED_ZOOM), 5.0f / scale, tweenAod.getValue()),
                )
                perspectiveCamera.up.set(Vector3.Y)
                perspectiveCamera.position.rotate(Vector3.Y, f2 * 10.0f).rotate(Vector3.Y, tweenRotation.getValue() * 10.0f)
                val lookAt: Vector3 = CAMERA_POSITIONS[cameraPosition].second
                aodLookAtComp.set(lookAt)
                aodLookAtComp.lerp(aodLookAt, tweenAod.getValue())
                perspectiveCamera.lookAt(aodLookAtComp)
            }

            override fun resetScene() {
                resetScene(DateUtils.getDayRatio())
            }

            protected fun resetScene(localDayRatio: Float) {
                super.resetScene()
                time = 0.0f
                cameraPosition =
                    when {
                        localDayRatio > 0.8333333f || localDayRatio <= 0.16666667f -> 2
                        localDayRatio > 0.45833334f -> 1
                        localDayRatio > 0.16666667f -> 0
                        else -> cameraPosition
                    }
                cameraPosition =
                    if (isPreviewFirst && cameraPosition == 2) {
                        1
                    } else {
                        cameraPosition
                    }
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
                    tweenRotation.set(PlanetEngine.LOCKED_ROTATION * 1.6f)
                    tweenZoom.set(2.0f)
                }
            }

            override fun getPreviewTiming(): Float = 4.0f

            override fun getPreviewEasing(): TweenController.Easing = TweenController.Easing.QUAD_OUT

            companion object {
                private const val LOCKED_ROTATION: Float = 10.0f
                private const val LOCKED_ZOOM: Float = 0.05f
                private const val MAX_ROTATION: Float = -60.0f
                private const val STARS_ROTATION_INTENSITY: Float = 0.1f
                private val CAMERA_POSITIONS: Array<Pair<Vector3, Vector3>> =
                    arrayOf(
                        Pair(Vector3(9.2f, 0.0f, -0.5f), Vector3()),
                        Pair(Vector3(9.8f, 0.0f, -1.5f), Vector3()),
                        Pair(Vector3(-9.2f, 0.0f, -3.7f), Vector3()),
                    )
                private val INITIAL_LIGHT_POSITION_SPHERICAL: SphericalTransform = SphericalTransform(1.1868238f, 1.1868238f, 70.0f)
            }
        }
}

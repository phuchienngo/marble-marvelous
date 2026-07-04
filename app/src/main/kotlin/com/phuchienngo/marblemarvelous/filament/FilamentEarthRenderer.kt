package com.phuchienngo.marblemarvelous.filament

import android.content.Context
import android.util.Log
import android.view.Surface
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.phuchienngo.marblemarvelous.earth.EarthLocationMath
import com.phuchienngo.marblemarvelous.location.GeoLocation
import com.phuchienngo.marblemarvelous.location.UserLocationEarth
import com.phuchienngo.marblemarvelous.math.Vec3
import com.phuchienngo.marblemarvelous.utils.DateUtils
import com.phuchienngo.marblemarvelous.utils.FrustumUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.Date
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal class FilamentEarthRenderer(
  context: Context,
  private val isPreview: Boolean
) {
  private val engine: Engine
  private val entityManager: EntityManager
  private var swapChain: SwapChain? = null
  private var attachedSurface: Surface? = null
  private val renderer: Renderer
  private val scene: Scene
  private val view: View
  private val cameraEntity: Int
  private val camera: Camera
  private val earthEntity: Int
  private val material: Material
  private val materialInstance: MaterialInstance
  private val earthTextures: FilamentEarthTextures
  private val vertexBuffer: VertexBuffer
  private val indexBuffer: IndexBuffer
  private val stars: FilamentStars
  private val userLocation: UserLocationEarth
  private val skybox: Skybox
  private var cameraAspectRatio: Float = 1.0f
  private var cameraVerticalFovDegrees: Float = CAMERA_FOV_DEGREES
  private var cameraNeedsUpdate: Boolean = true
  private var firstFrameTimeNanos: Long = 0L
  private var lastDateSampleMillis: Long = 0L
  private var cachedUtcDayRatio: Float = 0.0f
  private val earthTransform: FloatArray =
    floatArrayOf(
      1.0f, 0.0f, 0.0f, 0.0f,
      0.0f, 1.0f, 0.0f, 0.0f,
      0.0f, 0.0f, 1.0f, 0.0f,
      0.0f, 0.0f, 0.0f, 1.0f
    )

  init {
    val cleanupStack = FailureCleanupStack()
    try {
      FilamentRuntime.initialize()
      entityManager = EntityManager.get()
      engine = Engine.create(Engine.Backend.VULKAN)
      cleanupStack.register {
        engine.destroy()
      }
      Log.i(TAG, "Filament backend: ${engine.backend}")

      renderer = engine.createRenderer()
      cleanupStack.register {
        engine.destroyRenderer(renderer)
      }
      renderer.clearOptions = Renderer.ClearOptions().apply {
        clear = true
        discard = true
        clearColor = doubleArrayOf(0.0, 0.0, 0.0, 1.0)
      }

      scene = engine.createScene()
      cleanupStack.register {
        engine.destroyScene(scene)
      }
      view = engine.createView()
      cleanupStack.register {
        engine.destroyView(view)
      }
      view.scene = scene
      view.antiAliasing = View.AntiAliasing.NONE
      view.isPostProcessingEnabled = false

      skybox =
        Skybox
          .Builder()
          .color(0.0f, 0.0f, 0.012f, 1.0f)
          .build(engine)
      cleanupStack.register {
        engine.destroySkybox(skybox)
      }
      scene.skybox = skybox

      cameraEntity = entityManager.create()
      cleanupStack.register {
        entityManager.destroy(cameraEntity)
      }
      camera = engine.createCamera(cameraEntity)
      cleanupStack.register {
        engine.destroyCameraComponent(cameraEntity)
      }
      view.camera = camera
      userLocation = UserLocationEarth(context)
      cleanupStack.register {
        userLocation.dispose()
      }

      val mesh: FilamentEarthMeshData =
        context.assets.open(EARTH_MODEL_ASSET).use { input ->
          FilamentG3dbEarthMesh.load(input)
        }
      vertexBuffer = createVertexBuffer(mesh)
      cleanupStack.register {
        engine.destroyVertexBuffer(vertexBuffer)
      }
      indexBuffer = createIndexBuffer(mesh)
      cleanupStack.register {
        engine.destroyIndexBuffer(indexBuffer)
      }
      material = FilamentEarthMaterial.create(context, engine)
      cleanupStack.register {
        engine.destroyMaterial(material)
      }
      materialInstance = material.createInstance()
      cleanupStack.register {
        engine.destroyMaterialInstance(materialInstance)
      }
      earthTextures = FilamentEarthTextures.create(context, engine)
      cleanupStack.register {
        earthTextures.destroy(engine)
      }
      earthTextures.bind(materialInstance)
      materialInstance.setParameter(FilamentEarthMaterial.AURORA_ACTIVITY, DEFAULT_AURORA_ACTIVITY)

      earthEntity = entityManager.create()
      cleanupStack.register {
        entityManager.destroy(earthEntity)
      }
      RenderableManager
        .Builder(RENDERABLE_COUNT)
        .geometry(
          PRIMITIVE_INDEX,
          RenderableManager.PrimitiveType.TRIANGLES,
          vertexBuffer,
          indexBuffer,
          0,
          mesh.indexCount
        )
        .material(PRIMITIVE_INDEX, materialInstance)
        .boundingBox(
          Box(
            0.0f,
            0.0f,
            0.0f,
            mesh.boundingHalfExtent,
            mesh.boundingHalfExtent,
            mesh.boundingHalfExtent
          )
        )
        .culling(true)
        .castShadows(false)
        .receiveShadows(false)
        .build(engine, earthEntity)
      cleanupStack.register {
        engine.destroyEntity(earthEntity)
      }
      scene.addEntity(earthEntity)
      cleanupStack.register {
        scene.removeEntity(earthEntity)
      }

      stars = FilamentStars.create(context, engine, entityManager)
      cleanupStack.register {
        stars.destroy(engine, entityManager)
      }
      scene.addEntity(stars.entity)
      cleanupStack.register {
        scene.removeEntity(stars.entity)
      }
      cleanupStack.dismiss()
    } catch (throwable: Throwable) {
      cleanupStack.cleanUpFailure(throwable)
      throw throwable
    }
  }

  fun attachSurface(surface: Surface) {
    if (attachedSurface === surface && swapChain != null) {
      return
    }
    detachSurface()
    swapChain = engine.createSwapChain(surface)
    attachedSurface = surface
  }

  fun detachSurface() {
    val currentSwapChain: SwapChain = swapChain ?: return
    swapChain = null
    attachedSurface = null
    // The frame loop is already stopped before this runs, so just release the
    // swap chain. We intentionally do NOT flushAndWait() here: blocking the main
    // thread while the OS is tearing down the Surface risks an ANR. Filament
    // sequences the swap-chain teardown on its own backend thread. The engine's
    // pause state (set on visibility change) is left untouched.
    engine.destroySwapChain(currentSwapChain)
  }

  fun resize(
    width: Int,
    height: Int
  ) {
    val viewportWidth: Int = max(1, width)
    val viewportHeight: Int = max(1, height)
    view.viewport = Viewport(0, 0, viewportWidth, viewportHeight)

    val aspectRatio: Double = viewportWidth.toDouble() / viewportHeight.toDouble()
    val minSideFov: Float = FrustumUtils.vFovToHFov(CAMERA_FOV_DEGREES, REFERENCE_WIDTH, REFERENCE_HEIGHT)
    val verticalFov: Float =
      if (viewportWidth < viewportHeight) {
        FrustumUtils.hFovToVFov(minSideFov, viewportWidth.toFloat(), viewportHeight.toFloat())
      } else {
        minSideFov
      }
    cameraAspectRatio = aspectRatio.toFloat()
    cameraVerticalFovDegrees = verticalFov
    camera.setProjection(
      verticalFov.toDouble(),
      aspectRatio,
      CAMERA_NEAR,
      CAMERA_FAR,
      Camera.Fov.VERTICAL
    )
    cameraNeedsUpdate = true
    updateCameraIfNeeded()
  }

  fun setPaused(paused: Boolean) {
    engine.isPaused = paused
    if (!paused) {
      cameraNeedsUpdate = true
      firstFrameTimeNanos = 0L
      lastDateSampleMillis = 0L
    }
  }

  fun render(frameTimeNanos: Long) {
    val currentSwapChain: SwapChain = swapChain ?: return
    updateEarthTransform(frameTimeNanos)
    updateCameraIfNeeded()
    val elapsedSeconds: Float = (frameTimeNanos - firstFrameTimeNanos).toFloat() * NANOS_TO_SECONDS
    stars.setTime(elapsedSeconds)
    materialInstance.setParameter(FilamentEarthMaterial.TIME, elapsedSeconds)
    if (!renderer.beginFrame(currentSwapChain, frameTimeNanos)) {
      return
    }
    renderer.render(view)
    renderer.endFrame()
  }

  fun setAuroraActivity(activity: Float) {
    materialInstance.setParameter(
      FilamentEarthMaterial.AURORA_ACTIVITY,
      activity.coerceIn(0.0f, 1.0f)
    )
  }

  suspend fun reloadCloudMask(context: Context): Boolean =
    earthTextures.reloadCloudMask(context, engine, materialInstance)

  fun destroy() {
    detachSurface()
    userLocation.dispose()
    scene.removeEntity(earthEntity)
    scene.removeEntity(stars.entity)
    stars.destroy(engine, entityManager)
    engine.destroyEntity(earthEntity)
    entityManager.destroy(earthEntity)
    engine.destroyVertexBuffer(vertexBuffer)
    engine.destroyIndexBuffer(indexBuffer)
    engine.destroyMaterialInstance(materialInstance)
    engine.destroyMaterial(material)
    earthTextures.destroy(engine)
    engine.destroySkybox(skybox)
    engine.destroyCameraComponent(cameraEntity)
    entityManager.destroy(cameraEntity)
    engine.destroyView(view)
    engine.destroyScene(scene)
    engine.destroyRenderer(renderer)
    engine.destroy()
  }

  private fun createVertexBuffer(mesh: FilamentEarthMeshData): VertexBuffer {
    val buffer: VertexBuffer =
      VertexBuffer
        .Builder()
        .bufferCount(VERTEX_BUFFER_COUNT)
        .vertexCount(mesh.vertexCount)
        .attribute(
          VertexBuffer.VertexAttribute.POSITION,
          POSITION_BUFFER_INDEX,
          VertexBuffer.AttributeType.FLOAT3,
          0,
          POSITION_STRIDE_BYTES
        )
        .attribute(
          VertexBuffer.VertexAttribute.CUSTOM0,
          LOOKUP_NORMAL_BUFFER_INDEX,
          VertexBuffer.AttributeType.FLOAT4,
          0,
          LOOKUP_NORMAL_STRIDE_BYTES
        )
        .build(engine)
    buffer.setBufferAt(engine, POSITION_BUFFER_INDEX, directFloatBuffer(mesh.positions))
    buffer.setBufferAt(engine, LOOKUP_NORMAL_BUFFER_INDEX, directFloatBuffer(mesh.lookupNormals))
    return buffer
  }

  private fun createIndexBuffer(mesh: FilamentEarthMeshData): IndexBuffer {
    val buffer: IndexBuffer =
      IndexBuffer
        .Builder()
        .indexCount(mesh.indexCount)
        .bufferType(IndexBuffer.Builder.IndexType.USHORT)
        .build(engine)
    buffer.setBuffer(engine, directShortBuffer(mesh.indices))
    return buffer
  }

  private fun updateEarthTransform(frameTimeNanos: Long) {
    if (firstFrameTimeNanos == 0L) {
      firstFrameTimeNanos = frameTimeNanos
    }
    refreshDateSampleIfNeeded()
    val seconds: Float = (frameTimeNanos - firstFrameTimeNanos).toFloat() * NANOS_TO_SECONDS
    val angle: Float =
      FilamentEarthMotion.earthYawRadians(
        utcDayRatio = cachedUtcDayRatio,
        elapsedSeconds = seconds
      )
    val cosine: Float = cos(angle)
    val sine: Float = sin(angle)
    earthTransform[0] = cosine
    earthTransform[2] = -sine
    earthTransform[8] = sine
    earthTransform[10] = cosine
    val transformManager = engine.transformManager
    val transformInstance: Int = transformManager.getInstance(earthEntity)
    if (transformInstance == NO_TRANSFORM_INSTANCE) {
      return
    }
    transformManager.setTransform(transformInstance, earthTransform)
  }

  private fun updateCameraIfNeeded() {
    if (!cameraNeedsUpdate) {
      return
    }
    updateCamera()
    cameraNeedsUpdate = false
  }

  private fun updateCamera() {
    val now: Date = DateUtils.now()
    val utcDate: Date = DateUtils.getUTC(now) ?: now
    val beginningOfDay: Date = DateUtils.getAtBeginningOfDay(utcDate)
    val utcDayRatio: Float = (utcDate.time - beginningOfDay.time) / DateUtils.MILLIS_IN_A_DAY
    val location: GeoLocation = userLocation.lastKnown(requestPermissions = false)
    // Mesh-local direction of the user's location (no rotation): the earth shader
    // works in mesh-local space, so the marker rides the geography as it spins.
    val markerDirection: Vec3 =
      EarthLocationMath
        .locationSurface(
          longitudeDegrees = location.longitudeDegrees,
          latitudeDegrees = location.latitudeDegrees,
          radius = 1.0f,
          earthRotationDegrees = 0.0f
        )
        .normalized()
    materialInstance.setParameter(
      FilamentEarthMaterial.USER_LOCATION,
      markerDirection.x,
      markerDirection.y,
      markerDirection.z
    )
    val surface: Vec3 =
      EarthLocationMath.locationSurface(
        longitudeDegrees = location.longitudeDegrees,
        latitudeDegrees = location.latitudeDegrees,
        radius = EARTH_SURFACE_RADIUS,
        earthRotationDegrees = FULL_ROTATION_DEGREES * utcDayRatio
      )
    val cameraPosition: Vec3 =
      addToVectorSpace(
        main = surface,
        offset = FilamentCameraFraming.offsetFor(now, isPreview),
        up = CAMERA_UP
      )
    camera.lookAt(
      cameraPosition.x.toDouble(),
      cameraPosition.y.toDouble(),
      cameraPosition.z.toDouble(),
      surface.x.toDouble(),
      surface.y.toDouble(),
      surface.z.toDouble(),
      CAMERA_UP.x.toDouble(),
      CAMERA_UP.y.toDouble(),
      CAMERA_UP.z.toDouble()
    )
    stars.update(
      engine = engine,
      cameraPosition = cameraPosition,
      cameraTarget = surface,
      cameraUp = CAMERA_UP,
      aspectRatio = cameraAspectRatio,
      verticalFovDegrees = cameraVerticalFovDegrees
    )
  }

  private fun addToVectorSpace(
    main: Vec3,
    offset: Vec3,
    up: Vec3
  ): Vec3 {
    val direction: Vec3 = main.normalized()
    val left: Vec3 = direction.cross(up).normalized()
    return main
      .add(direction.cross(up).scale(offset.x))
      .add(direction.cross(left).scale(offset.y))
      .add(direction.scale(offset.z))
  }

  private fun refreshDateSampleIfNeeded() {
    val nowMillis: Long = System.currentTimeMillis()
    if (lastDateSampleMillis != 0L &&
      nowMillis - lastDateSampleMillis < DATE_SAMPLE_INTERVAL_MILLIS
    ) {
      return
    }
    lastDateSampleMillis = nowMillis
    val utcDate: Date = DateUtils.getUTC(DateUtils.now()) ?: DateUtils.now()
    val beginningOfDay: Date = DateUtils.getAtBeginningOfDay(utcDate)
    cachedUtcDayRatio = (utcDate.time - beginningOfDay.time) / DateUtils.MILLIS_IN_A_DAY
    updateSunDirection(
      dayOfYear = DateUtils.getDayOfYear(utcDate),
      earthRotationRadians = FilamentEarthMotion.realtimeYawRadians(cachedUtcDayRatio)
    )
  }

  private fun updateSunDirection(
    dayOfYear: Int,
    earthRotationRadians: Float
  ) {
    val sunDirection: Vec3 =
      FilamentSunDirection.localDirectionForEarthRotation(
        dayOfYear = dayOfYear,
        earthRotationRadians = earthRotationRadians
      )
    materialInstance.setParameter(
      FilamentEarthMaterial.SUN_DIRECTION,
      sunDirection.x,
      sunDirection.y,
      sunDirection.z
    )
  }

  companion object {
    private fun directFloatBuffer(values: FloatArray): FloatBuffer {
      val buffer: FloatBuffer =
        ByteBuffer
          .allocateDirect(values.size * FLOAT_BYTES)
          .order(ByteOrder.nativeOrder())
          .asFloatBuffer()
      buffer.put(values)
      buffer.flip()
      return buffer
    }

    private fun directShortBuffer(values: ShortArray): ShortBuffer {
      val buffer: ShortBuffer =
        ByteBuffer
          .allocateDirect(values.size * SHORT_BYTES)
          .order(ByteOrder.nativeOrder())
          .asShortBuffer()
      buffer.put(values)
      buffer.flip()
      return buffer
    }

    private const val CAMERA_FAR: Double = 20.0
    private const val CAMERA_FOV_DEGREES: Float = 20.0f
    private const val CAMERA_NEAR: Double = 0.1
    private const val DATE_SAMPLE_INTERVAL_MILLIS: Long = 1000L
    private const val DEFAULT_AURORA_ACTIVITY: Float = 0.25f
    private const val EARTH_MODEL_ASSET: String = "earth/earth.g3db"
    private const val EARTH_SURFACE_RADIUS: Float = 0.6570345f
    private const val FLOAT_BYTES: Int = 4
    private const val FULL_ROTATION_DEGREES: Float = 360.0f
    private const val LOOKUP_NORMAL_BUFFER_INDEX: Int = 1
    private const val LOOKUP_NORMAL_STRIDE_BYTES: Int = 16
    private const val NANOS_TO_SECONDS: Float = 0.000000001f
    private const val NO_TRANSFORM_INSTANCE: Int = 0
    private const val POSITION_BUFFER_INDEX: Int = 0
    private const val POSITION_STRIDE_BYTES: Int = 12
    private const val PRIMITIVE_INDEX: Int = 0
    private const val RENDERABLE_COUNT: Int = 1
    private const val SHORT_BYTES: Int = 2
    private const val VERTEX_BUFFER_COUNT: Int = 2
    private const val REFERENCE_HEIGHT: Float = 16.0f
    private const val REFERENCE_WIDTH: Float = 9.0f
    private const val TAG: String = "FilamentWallpaper"
    private val CAMERA_UP: Vec3 = Vec3.UP
  }
}

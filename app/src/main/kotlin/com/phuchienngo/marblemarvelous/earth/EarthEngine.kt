package com.phuchienngo.marblemarvelous.earth

import android.content.Context
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.badlogic.gdx.assets.loaders.CubemapLoader
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.CubemapData
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g3d.Environment
import com.badlogic.gdx.graphics.g3d.Model
import com.badlogic.gdx.graphics.g3d.ModelBatch
import com.badlogic.gdx.graphics.g3d.ModelInstance
import com.badlogic.gdx.graphics.g3d.attributes.CubemapAttribute
import com.badlogic.gdx.graphics.g3d.environment.PointLight
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.graphics.glutils.KTXTextureData
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.phuchienngo.marblemarvelous.animations.TweenController
import com.phuchienngo.marblemarvelous.earth.core.Glow
import com.phuchienngo.marblemarvelous.earth.core.Stars
import com.phuchienngo.marblemarvelous.earth.shader.EarthMask
import com.phuchienngo.marblemarvelous.earth.shader.EarthShaderProvider
import com.phuchienngo.marblemarvelous.earth.shader.attributes.EarthTextureAttribute
import com.phuchienngo.marblemarvelous.gl.FramebufferHints
import com.phuchienngo.marblemarvelous.input.InputMultiplexer
import com.phuchienngo.marblemarvelous.location.UserLocationEarth
import com.phuchienngo.marblemarvelous.power.FPSThrottler
import com.phuchienngo.marblemarvelous.power.RenderPowerInput
import com.phuchienngo.marblemarvelous.power.RenderPowerPolicy
import com.phuchienngo.marblemarvelous.power.RenderPowerProfile
import com.phuchienngo.marblemarvelous.power.ThermalLevel
import com.phuchienngo.marblemarvelous.utils.DateUtils
import com.phuchienngo.marblemarvelous.utils.FrustumUtils
import com.phuchienngo.marblemarvelous.utils.ScreenSizeLimiter
import com.phuchienngo.marblemarvelous.utils.ShaderUtils
import com.phuchienngo.marblemarvelous.wallpaper.BaseWallpaperEngine
import com.phuchienngo.marblemarvelous.wallpaper.WallpaperSurfaceMath
import com.phuchienngo.marblemarvelous.wallpaper.WallpaperSurfaceOrigin
import com.phuchienngo.marblemarvelous.wallpaper.WallpaperSurfaceSize
import com.phuchienngo.marblemarvelous.wallpaper.WallpaperThemeProcessor
import com.phuchienngo.marblemarvelous.wallpaper.controller.UserPresenceController
import com.phuchienngo.marblemarvelous.weather.CloudsProvider
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import kotlin.math.tan

class EarthEngine(
  private val context: Context,
  private val cloudsProviderFactory: CloudsProvider.Factory,
  private val userLocation: UserLocationEarth,
  inputMultiplexer: InputMultiplexer,
  fpsThrottler: FPSThrottler
) : BaseWallpaperEngine(inputMultiplexer, fpsThrottler),
  CloudsProvider.Callback,
  WallpaperThemeProcessor {
  private var assetManager: AssetManager? = null
  private var batch: SpriteBatch? = null
  private var batchComposed: SpriteBatch? = null
  private var cam: PerspectiveCamera? = null
  private var cloudDetailTexture: Cubemap? = null
  private var cloudsProvider: CloudsProvider? = null
  private var cloudsTexture: Cubemap? = null
  private var diffuse: Cubemap? = null
  private var environment: Environment? = null
  private var earthShaderProvider: EarthShaderProvider? = null
  private var fbo: FrameBuffer? = null
  private var finalShader: ShaderProgram? = null
  private var glow: Glow? = null
  private var glowCamera: OrthographicCamera? = null
  private var mEarth: ModelInstance? = null
  private var mask: EarthMask? = null
  private var model: Model? = null
  private var modelBatch: ModelBatch? = null
  private var nextCloudMap: CubemapData? = null
  private var nightDiffuse: Cubemap? = null
  private var baseScaledSize: WallpaperSurfaceSize = WallpaperSurfaceSize(width = 1, height = 1)
  private var scaledSize: WallpaperSurfaceSize = WallpaperSurfaceSize(width = 1, height = 1)
  private var visibleSize: WallpaperSurfaceSize = WallpaperSurfaceSize(width = 1, height = 1)
  private var season: String? = null
  private var stars: Stars? = null
  private var sunLight: PointLight? = null
  private var sunLightPosition: Vector3? = null
  private var tintGlow: ShaderProgram? = null

  @Volatile
  private var loading: Boolean = true
  private val glowColorUnlocked: Color = Color(0.1254902f, 0.22352941f, 0.3529412f, 0.7f)
  private val glowColorAOD: Color = Color(0.5176471f, 0.6156863f, 0.9019608f, 0.9f)
  private val glowColor: Color = Color()
  private val cameraPositions: ArrayList<Vector3> = ArrayList()
  private var camPosition: Vector3 = Vector3()
  private val currentCameraPos: Vector3 = Vector3()
  private val currentModelTransform: Matrix4 = Matrix4()
  private val zoomDirection: Vector3 = Vector3()
  private val finalCameraPos: Vector3 = Vector3()
  private val finalSunlightPosition: Vector3 = Vector3()
  private val finalModelTransform: Matrix4 = Matrix4()
  private var needsCloudsUpdate: Boolean = true
  private var pendingResumeCloudRefresh: Boolean = false
  private var prevCameraPositionIndex: Int = -1
  private var renderPowerProfile: RenderPowerProfile =
    RenderPowerPolicy.select(
      RenderPowerInput(
        isResumeWarmupActive = true,
        isTweenAnimating = false,
        isScrollAnimating = false,
        isPowerSave = false,
        thermalLevel = ThermalLevel.NORMAL
      )
    )
  private val tweenAod: TweenController = TweenController()
  private val tweenRotation: TweenController = TweenController()
  private val tweenZoom: TweenController = TweenController()

  init {
    tweenAod.set(0.0f)
    tweenZoom.set(0.0f)
    tweenRotation.set(0.0f)
    pageSwipeController.setMinPagesToSwipe(4)
    pageSwipeController.setPageSwipeDamping(PAGE_CHANGING_DAMPING)
  }

  override fun initialize() {
    super.initialize()
    assetManager = AssetManager()
    assetManager!!.setLoader(Cubemap::class.java, CubemapLoader(InternalFileHandleResolver()))
    assetManager!!.load("earth/earth.g3db", Model::class.java)
    assetManager!!.load("earth/nightMap.ktx", Cubemap::class.java)
    assetManager!!.load("earth/clouds.ktx", Cubemap::class.java)
    val shaderProvider: EarthShaderProvider = EarthShaderProvider()
    earthShaderProvider = shaderProvider
    modelBatch = ModelBatch(shaderProvider)
    sunLight = PointLight()
    sunLightPosition = EarthLocationMath.sunLightPosition(0.0f).scl(INITIAL_LIGHT_DISTANCE)
    environment = Environment()
    environment!!.add(sunLight)
    cameraPositions.add(Vector3(0.0f, 0.0f, 13.75f))
    cameraPositions.add(Vector3(0.0f, 0.0f, 9.85f))
    cameraPositions.add(Vector3(4.65f, 0.0f, 6.85f))
    stars = Stars(cam)
    cloudsProvider = cloudsProviderFactory.create(this)
    val w: Int = screenSize!!.getWidth().toInt()
    val h: Int = screenSize!!.getHeight().toInt()
    updateVisibleSize(w, h)
    updateBaseScaledSize()
    scaledSize = scaledSizeForProfile(renderPowerProfile)
    initFbo()
    mask = EarthMask()
    mask!!.setRimFade(0.78f, 0.81f)
    glow = Glow()
    tintGlow = ShaderUtils.load("marble/tintGlow")
    tintGlow!!.bind()
    tintGlow!!.setUniformf("u_tint_color", glowColor)
    tintGlow!!.setUniformf("u_resolution", screenSize!!.getWidth(), screenSize!!.getHeight())
    glowCamera = OrthographicCamera()
    val yDown: Boolean = true
    glowCamera!!.setToOrtho(yDown, screenSize!!.getWidth(), screenSize!!.getHeight())
    batch = SpriteBatch()
    batch!!.projectionMatrix = glowCamera!!.combined
    batch!!.shader = tintGlow
    batchComposed = SpriteBatch(1)
    batchComposed!!.projectionMatrix.setToOrtho2D(0.0f, 0.0f, w.toFloat(), h.toFloat())
    finalShader = ShaderUtils.load("earth/final")
    batchComposed!!.shader = finalShader
    updateRenderTargetProjection()
  }

  @Synchronized
  override fun resize(
    width: Int,
    height: Int
  ) {
    super.resize(width, height)
    Gdx.gl.glViewport(0, 0, width, height)
    updateVisibleSize(width, height)
    updateBaseScaledSize()
    scaledSize = scaledSizeForProfile(renderPowerProfile)
    initFbo()
    batchComposed!!.projectionMatrix.setToOrtho2D(0.0f, 0.0f, width.toFloat(), height.toFloat())
    updateRenderTargetProjection()
    setEarthAndCameraPosition()
  }

  @Synchronized
  override fun resume() {
    super.resume()
    if (isLoaded()) {
      setEarthAndCameraPosition()
      pendingResumeCloudRefresh = true
    }
  }

  @Synchronized
  override fun updateWallpaper(delta: Float) {
    super.updateWallpaper(delta)
    if (loading && assetManager!!.update()) {
      doneLoading()
      zoomIn(presence = getUserPresence(), animate = true)
    }
    if (loading) {
      return
    }
    tweenRotation.update(delta)
    tweenAod.update(delta)
    tweenZoom.update(delta)
    renderPowerProfile = selectRenderPowerProfile()
    maybeUpdateCloudsAfterResumeWarmup()
    applyPendingCloudMapIfAllowed()
    sunLight!!.setIntensity(FOV_INCREASE - tweenAod.getValue())
    stars!!.setAOD(tweenAod.getValue())
    mask!!.setAOD(tweenAod.getValue())
    mask!!.setFieldOfViewDifference(FOV_INCREASE)
    glowColor.set(glowColorUnlocked)
    glowColor.lerp(glowColorAOD, tweenAod.getValue())
    animate(delta)
    holdWakelockAOD(tweenAod.isAnimating())
  }

  private fun maybeUpdateCloudsAfterResumeWarmup() {
    if (!pendingResumeCloudRefresh || !renderPowerProfile.allowCloudRefresh) {
      return
    }
    pendingResumeCloudRefresh = false
    cloudsProvider?.updateClouds()
  }

  @Synchronized
  override fun renderWallpaper(): Int {
    super.renderWallpaper()
    val profile: RenderPowerProfile = renderPowerProfile
    applyRenderPowerProfile(profile)
    val targetFbo: FrameBuffer =
      requireNotNull(fbo) renderTargetLoaded@{
        return@renderTargetLoaded "Render target framebuffer must be initialized before rendering."
      }
    mask!!.begin(cam!!, null)
    mask!!.render(mEarth!!, environment!!)
    mask!!.end()
    val glowTexture: Texture = glow!!.generateGlow(mask!!.getFboTexture(), profile.glowPasses)
    targetFbo.begin()
    FramebufferHints.discardPreviousColorAttachment()
    Gdx.gl.glViewport(0, 0, scaledSize.width, scaledSize.height)
    Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT or GL20.GL_DEPTH_BUFFER_BIT)
    stars!!.begin(cam!!)
    stars!!.render()
    stars!!.end()
    val diff: Float =
      (
          tan(((cam!!.fieldOfView + FOV_INCREASE) * 0.017453292f).toDouble()) /
              tan((cam!!.fieldOfView * 0.017453292f).toDouble())
          ).toFloat()
    val renderTargetWidth: Float = scaledSize.width.toFloat()
    val renderTargetHeight: Float = scaledSize.height.toFloat()
    val dW: Float = renderTargetWidth * (diff - FOV_INCREASE)
    val dH: Float = (diff - FOV_INCREASE) * renderTargetHeight
    batch!!.begin()
    batch!!.enableBlending()
    tintGlow!!.setUniformf("u_tint_color", glowColor)
    batch!!.draw(
      glowTexture,
      -dW / ACTIVATE_ROTATION,
      -dH / ACTIVATE_ROTATION,
      renderTargetWidth + dW,
      renderTargetHeight + dH
    )
    batch!!.end()
    cam!!.update()
    modelBatch!!.begin(cam)
    modelBatch!!.render(mEarth, environment)
    modelBatch!!.end()
    targetFbo.end()
    composeRenderTarget(targetFbo.colorBufferTexture)
    return profile.targetFps
  }

  @Synchronized
  override fun dispose() {
    super.dispose()
    val currentCloudsProvider: CloudsProvider? = cloudsProvider
    if (currentCloudsProvider != null) {
      currentCloudsProvider.dispose()
      cloudsProvider = null
    }
    val currentCloudsTexture: Cubemap? = cloudsTexture
    if (currentCloudsTexture != null) {
      currentCloudsTexture.dispose()
      cloudsTexture = null
    }
    val currentAssetManager: AssetManager? = assetManager
    if (currentAssetManager != null) {
      currentAssetManager.dispose()
      assetManager = null
    }
    val currentStars: Stars? = stars
    if (currentStars != null) {
      currentStars.dispose()
      stars = null
    }
    batch?.dispose()
    batch = null
    batchComposed?.dispose()
    batchComposed = null
    modelBatch?.dispose()
    modelBatch = null
    earthShaderProvider = null
    fbo?.dispose()
    fbo = null
    diffuse?.dispose()
    diffuse = null
    disposeCloudCubeMap(nextCloudMap)
    nextCloudMap = null
    mask?.dispose()
    mask = null
    glow?.dispose()
    glow = null
    finalShader?.dispose()
    finalShader = null
    tintGlow?.dispose()
    tintGlow = null
    nightDiffuse = null
    cloudDetailTexture = null
    model = null
    mEarth = null
  }

  override fun previewStateChange(isPreview: Boolean) {
    super.previewStateChange(isPreview)
    if (isPreviewFirst) {
      zoomIn(presence = UserPresenceController.PRESENCE_LOCKED, animate = false)
    }
    if (isLoaded()) {
      setEarthAndCameraPosition()
    }
    if (nextCloudMap == null) {
      nextCloudMap = cloudsProvider!!.getLatest()
    }
  }

  @Synchronized
  override fun userPresenceChange(
    newUserPresence: String,
    prevUserPresence: String,
    animate: Boolean
  ) {
    super.userPresenceChange(newUserPresence, prevUserPresence, animate)
    if ((
          (!animate && newUserPresence == UserPresenceController.PRESENCE_AOD) ||
              newUserPresence == UserPresenceController.PRESENCE_OFF
          ) && nextCloudMap != null
    ) {
      needsCloudsUpdate = true
      setEarthAndCameraPosition()
    }
    zoomIn(newUserPresence, animate)
  }

  @Synchronized
  override fun onCloudsUpdated() {
    val currentCloudsProvider: CloudsProvider =
      requireNotNull(cloudsProvider) cloudsProviderLoaded@{
        return@cloudsProviderLoaded "CloudsProvider must be initialized before clouds can update."
      }
    val latestCloudMap: CubemapData? = currentCloudsProvider.getLatest()
    if (latestCloudMap != null) {
      val pending: CubemapData? = nextCloudMap
      disposeCloudCubeMap(pending)
      nextCloudMap = latestCloudMap
      needsCloudsUpdate = true
    }
  }

  private fun applyPendingCloudMapIfAllowed() {
    val pending: CubemapData = nextCloudMap ?: return
    if (!needsCloudsUpdate) {
      return
    }
    if (cloudsTexture != null && !renderPowerProfile.allowCloudRefresh) {
      return
    }
    cloudsTexture?.dispose()
    val cloudMap = Cubemap(pending)
    cloudsTexture = cloudMap
    environment!!.set(CubemapAttribute(CubemapAttribute.EnvironmentMap, cloudMap))
    cloudMap.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    disposeCloudCubeMap(pending)
    nextCloudMap = null
    needsCloudsUpdate = false
  }

  private fun disposeCloudCubeMap(cubemapData: CubemapData?) {
    if (cubemapData is CloudsProvider.CloudCubeMap) {
      cubemapData.dispose()
    }
  }

  private fun doneLoading() {
    loading = false
    setWallpaperReady()
    val currentCloudsProvider: CloudsProvider =
      requireNotNull(cloudsProvider) cloudsProviderReady@{
        return@cloudsProviderReady "CloudsProvider must be initialized before loading completes."
      }
    nightDiffuse = assetManager!!.get("earth/nightMap.ktx", Cubemap::class.java)
    nightDiffuse!!.setFilter(
      Texture.TextureFilter.MipMapLinearLinear,
      Texture.TextureFilter.Linear
    )
    cloudDetailTexture = assetManager!!.get("earth/clouds.ktx", Cubemap::class.java)
    cloudDetailTexture!!.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
    model = assetManager!!.get("earth/earth.g3db", Model::class.java)
    mEarth = ModelInstance(model)
    mEarth!!.userData = "earth"
    val loadedNightDiffuse: Cubemap =
      requireNotNull(nightDiffuse) nightDiffuseLoaded@{
        return@nightDiffuseLoaded "Night diffuse map must be loaded before material setup."
      }
    val loadedCloudDetail: Cubemap =
      requireNotNull(cloudDetailTexture) cloudDetailLoaded@{
        return@cloudDetailLoaded "Cloud detail map must be loaded before material setup."
      }
    mEarth!!.materials.first().set(EarthTextureAttribute.createNight(loadedNightDiffuse))
    mEarth!!.materials.first().set(EarthTextureAttribute.createCloudDetail(loadedCloudDetail))
    if (nextCloudMap == null) {
      nextCloudMap = currentCloudsProvider.getLatest()
    }
    setEarthAndCameraPosition()
    onCloudsUpdated()
    if (renderPowerProfile.allowCloudRefresh) {
      currentCloudsProvider.updateClouds()
    } else {
      pendingResumeCloudRefresh = true
    }
  }

  @Synchronized
  protected fun setEarthAndCameraPosition() {
    setEarthAndCameraPosition(DateUtils.now())
  }

  @Synchronized
  protected fun setEarthAndCameraPosition(date: Date) {
    if (mEarth == null) {
      return
    }
    val utcDate: Date? = DateUtils.getUTC(date)
    val month: Int = Calendar.getInstance().get(2) + 1
    val newSeason: String =
      when {
        month > 11 || month < 3 -> "Winter"
        month > 5 && month < 9 -> "Summer"
        else -> "Spring-Fall"
      }
    if (newSeason != season) {
      season = newSeason
      diffuse?.dispose()
      val useMipMaps: Boolean = false
      diffuse = Cubemap(KTXTextureData(Gdx.files.internal("earth/dayMap-$season.ktx"), useMipMaps))
      diffuse!!.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
      val loadedDiffuse: Cubemap =
        requireNotNull(diffuse) dayDiffuseLoaded@{
          return@dayDiffuseLoaded "Day diffuse map must be loaded before material setup."
        }
      mEarth!!.materials.first().set(EarthTextureAttribute.createDay(loadedDiffuse))
    }
    val beginningOfDay: Date = DateUtils.getAtBeginningOfDay(utcDate!!)
    val durationMS: Long = utcDate.time - beginningOfDay.time
    val dayRatio: Float = durationMS / 8.64E7f
    val dayOfTheYear: Int = DateUtils.getDayOfYear(utcDate)
    val sunDeclination: Float = EarthLocationMath.sunDeclination(dayOfTheYear)
    sunLightPosition!!.set(
      EarthLocationMath.sunLightPosition(sunDeclination).scl(INITIAL_LIGHT_DISTANCE)
    )
    val earthTransform: Matrix4 = Matrix4().idt()
    earthTransform.rotate(Vector3.Y, 360.0f * dayRatio)
    currentModelTransform.idt().mul(earthTransform)
    val width: Int = visibleSize.width
    val height: Int = visibleSize.height
    val minSideFov: Float = FrustumUtils.vFovToHFov(CAMERA_FOV, 9.0f, 16.0f)
    val fovAdj: Float =
      if (width < height) {
        FrustumUtils.hFovToVFov(minSideFov, width.toFloat(), height.toFloat())
      } else {
        minSideFov
      }
    cam = PerspectiveCamera(fovAdj, scaledSize.width.toFloat(), scaledSize.height.toFloat())
    cam!!.near = 0.0f
    cam!!.far = 300.0f
    val box: BoundingBox = BoundingBox()
    mEarth!!.calculateBoundingBox(box)
    val earthRadius: Float = box.depth / ACTIVATE_ROTATION
    val location: Vector2 = userLocation.lastKnown(requestPermissions = false)
    val surface: Vector3 =
      EarthLocationMath.locationSurface(location.x, location.y, earthRadius, earthTransform)
    val cameraPositionIndex: Int = getCameraPositionIndex(date)
    camPosition = cameraPositions[cameraPositionIndex].cpy()
    if (cameraPositionIndex == 2 && width > height) {
      camPosition.y = (camPosition.y + 0.3).toFloat()
    }
    cam!!.position.set(surface.cpy().scl(ACTIVATE_ROTATION))
    cam!!.lookAt(surface)
    currentCameraPos.set(addToVectorSpace(surface, camPosition, cam!!.up))
    cam!!.position.set(currentCameraPos)
    cam!!.lookAt(surface)
    if (prevCameraPositionIndex == -1 || prevCameraPositionIndex != cameraPositionIndex) {
      stars!!.updateStarsPositions(cam)
    }
    prevCameraPositionIndex = cameraPositionIndex
  }

  private fun addToVectorSpace(
    main: Vector3,
    offset: Vector3,
    up: Vector3
  ): Vector3 {
    val direction: Vector3 = main.cpy().nor()
    val combined: Vector3 = main.cpy()
    val left: Vector3 = direction.cpy().crs(up).nor()
    combined.add(direction.cpy().crs(up).scl(offset.x))
    combined.add(direction.cpy().crs(left).scl(offset.y))
    combined.add(direction.cpy().scl(offset.z))
    return combined
  }

  fun animate(delta: Float) {
    zoomDirection.set(currentCameraPos).nor()
    finalCameraPos.set(currentCameraPos)
    finalSunlightPosition.set(sunLightPosition)
    finalModelTransform.set(currentModelTransform)
    val cameraDistanceRatio: Float = camPosition.z / PAGE_CHANGE_ROTATION_MULTIPLIER
    val moveLength: Float = finalCameraPos.len() * 0.1f * cameraDistanceRatio
    val amountToMove: Float = tweenZoom.getValue() * moveLength
    finalCameraPos.add(
      zoomDirection.x * amountToMove,
      zoomDirection.y * amountToMove,
      zoomDirection.z * amountToMove
    )
    val zOffset: Float = pageSwipeController.getPageOffset()
    finalCameraPos.sub(
      zoomDirection.x * zOffset * finalCameraPos.len() * 0.03f,
      zoomDirection.y * zOffset * finalCameraPos.len() * 0.03f,
      zoomDirection.z * zOffset * finalCameraPos.len() * 0.03f
    )
    finalModelTransform.rotate(
      Vector3.Y,
      -tweenRotation.getValue() * ACTIVATE_ROTATION * cameraDistanceRatio
    )
    finalModelTransform.rotate(Vector3.Y, -tweenZoom.getValue() * 10.0f * cameraDistanceRatio)
    finalSunlightPosition.rotate(Vector3.Y, -tweenZoom.getValue() * 10.0f * cameraDistanceRatio)
    finalModelTransform.rotate(
      Vector3.Y,
      -zOffset * PAGE_CHANGE_ROTATION_MULTIPLIER * cameraDistanceRatio
    )
    finalSunlightPosition.rotate(
      Vector3.Y,
      -zOffset * PAGE_CHANGE_ROTATION_MULTIPLIER * cameraDistanceRatio
    )
    stars!!.transform.rotation.set(
      Vector3.Y,
      -zOffset * PAGE_CHANGE_ROTATION_MULTIPLIER * STARS_MOTION_INTENSITY
    )
    currentModelTransform.rotate(Vector3.Y, IDLE_ROTATION_SPEED * delta)
    sunLightPosition!!.rotate(Vector3.Y, IDLE_ROTATION_SPEED * delta)
    cam!!.position.set(finalCameraPos)
    cam!!.update()
    sunLightPosition!!.rotate(Vector3.Y, EARTH_ROTATION_SPEED_SEC * delta)
    mEarth!!.transform.set(finalModelTransform)
    sunLight!!.position.set(finalSunlightPosition)
  }

  private fun zoomIn(
    presence: String,
    animate: Boolean
  ) {
    val easingAod: TweenController.Easing = TweenController.Easing.QUAD_OUT
    val easing: TweenController.Easing = TweenController.Easing.QUAD_OUT
    var targetAod = 0.0f
    var targetZoom = 0.0f
    var targetRotation = 0.0f
    var delay = 0.0f
    var animateUpdate = animate
    when (presence) {
      UserPresenceController.PRESENCE_LOCKED -> {
        targetZoom = 0.5f
      }

      UserPresenceController.PRESENCE_OFF -> {
        animateUpdate = !isPaused
        delay =
          if (!animateUpdate) {
            0.0f
          } else {
            0.2f
          }
        targetZoom = FOV_INCREASE
        targetAod = FOV_INCREASE
        targetRotation = FOV_INCREASE
      }

      UserPresenceController.PRESENCE_AOD -> {
        targetZoom = FOV_INCREASE
        targetAod = FOV_INCREASE
        targetRotation = FOV_INCREASE
      }
    }
    if (animateUpdate) {
      tweenAod.to(targetAod, PAGE_CHANGING_DAMPING, delay, easingAod)
      tweenRotation.to(targetRotation, 3.0f, delay, easing)
      tweenZoom.to(targetZoom, 3.0f, delay, easing)
    } else {
      tweenAod.set(targetAod)
      tweenRotation.set(targetRotation)
      tweenZoom.set(targetZoom)
    }
  }

  protected fun getCameraPositionIndex(): Int = getCameraPositionIndex(DateUtils.now())

  private fun getCameraPositionIndex(date: Date): Int {
    val localDayRatio: Float = DateUtils.getDayRatio(date)
    if (localDayRatio > 0.7083333f) {
      return 0
    }
    if (localDayRatio > 0.5f) {
      return 1
    }
    return if (localDayRatio > 0.20833333f) {
      2
    } else {
      0
    }
  }

  private fun initFbo() {
    fbo?.dispose()
    val hasDepth: Boolean = false
    fbo = FrameBuffer(Pixmap.Format.RGBA8888, scaledSize.width, scaledSize.height, hasDepth)
    fbo!!.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
  }

  private fun applyRenderPowerProfile(profile: RenderPowerProfile) {
    renderPowerProfile = profile
    setPreferredSurfaceFrameRate(profile.surfaceFrameRate)
    earthShaderProvider?.setShaderQuality(profile.shaderQuality)
    val nextScaledSize: WallpaperSurfaceSize = scaledSizeForProfile(profile)
    if (nextScaledSize == scaledSize) {
      return
    }
    scaledSize = nextScaledSize
    initFbo()
    updateRenderTargetProjection()
    updateCameraViewport()
  }

  private fun selectRenderPowerProfile(): RenderPowerProfile =
    RenderPowerPolicy.select(
      RenderPowerInput(
        isResumeWarmupActive = isResumeWarmupActive(),
        isTweenAnimating = tweenRotation.isAnimating() || tweenZoom.isAnimating() || tweenAod.isAnimating(),
        isScrollAnimating = pageSwipeController.isScrollAnimating(),
        isPowerSave = isPowerSave(),
        thermalLevel = getThermalLevel()
      )
    )

  private fun scaledSizeForProfile(profile: RenderPowerProfile): WallpaperSurfaceSize =
    WallpaperSurfaceMath.scaledSize(
      baseSize = baseScaledSize,
      scale = profile.renderScale,
      minSize = MIN_RENDER_TARGET_SIZE
    )

  private fun updateRenderTargetProjection() {
    val renderTargetWidth: Float = scaledSize.width.toFloat()
    val renderTargetHeight: Float = scaledSize.height.toFloat()
    val yDown: Boolean = true
    glowCamera?.setToOrtho(yDown, renderTargetWidth, renderTargetHeight)
    val currentGlowCamera: OrthographicCamera? = glowCamera
    if (currentGlowCamera != null) {
      batch?.projectionMatrix = currentGlowCamera.combined
    }
    glow?.resize(scaledSize.width, scaledSize.height)
    tintGlow?.bind()
    tintGlow?.setUniformf("u_resolution", renderTargetWidth, renderTargetHeight)
  }

  private fun updateCameraViewport() {
    val camera: PerspectiveCamera? = cam
    if (camera == null) {
      setEarthAndCameraPosition()
      return
    }
    camera.viewportWidth = scaledSize.width.toFloat()
    camera.viewportHeight = scaledSize.height.toFloat()
    camera.update()
  }

  private fun composeRenderTarget(texture: Texture) {
    val surfaceWidth: Int = screenSize!!.getWidth().toInt()
    val surfaceHeight: Int = screenSize!!.getHeight().toInt()
    val surfaceSize: WallpaperSurfaceSize =
      WallpaperSurfaceSize(width = surfaceWidth, height = surfaceHeight)
    val drawOrigin: WallpaperSurfaceOrigin =
      WallpaperSurfaceMath.visibleDrawOrigin(
        surfaceSize = surfaceSize,
        visibleSize = visibleSize,
        offsetX = visibleSurfaceOffsetX,
        offsetY = visibleSurfaceOffsetY
      )
    Gdx.gl.glViewport(0, 0, surfaceWidth, surfaceHeight)
    Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)
    batchComposed!!.begin()
    batchComposed!!.disableBlending()
    batchComposed!!.draw(
      texture,
      drawOrigin.x.toFloat(),
      drawOrigin.y.toFloat(),
      visibleSize.width.toFloat(),
      visibleSize.height.toFloat()
    )
    batchComposed!!.end()
  }

  private fun updateVisibleSize(
    surfaceWidth: Int,
    surfaceHeight: Int
  ) {
    val metrics = context.resources.displayMetrics
    val displayIsLandscape: Boolean = metrics.widthPixels >= metrics.heightPixels
    val surfaceIsLandscape: Boolean = surfaceWidth >= surfaceHeight
    val displayWidth: Int =
      if (displayIsLandscape == surfaceIsLandscape) {
        metrics.widthPixels
      } else {
        metrics.heightPixels
      }
    val displayHeight: Int =
      if (displayIsLandscape == surfaceIsLandscape) {
        metrics.heightPixels
      } else {
        metrics.widthPixels
      }
    visibleSize =
      WallpaperSurfaceMath.visibleSize(
        surfaceWidth = surfaceWidth,
        surfaceHeight = surfaceHeight,
        displayWidth = displayWidth,
        displayHeight = displayHeight
      )
  }

  private fun updateBaseScaledSize() {
    val nextBaseScaledSize: IntArray =
      ScreenSizeLimiter.getScaledSize(
        visibleSize.width,
        visibleSize.height,
        SCALED_WIDTH,
        SCALED_HEIGHT
      )
    baseScaledSize =
      WallpaperSurfaceSize(
        width = nextBaseScaledSize[0],
        height = nextBaseScaledSize[1]
      )
  }

  override fun darkText(): Boolean = false

  override fun darkTheme(): Boolean = true

  companion object {
    private const val ACTIVATE_ROTATION: Float = 2.0f
    private const val CAMERA_FOV: Float = 20.0f
    private const val EARTH_ROTATION_SPEED_SEC: Float = 0.004166667f
    private const val FOV_INCREASE: Float = 1.0f
    private const val IDLE_ROTATION_SPEED: Float = 0.85f
    private const val INITIAL_LIGHT_DISTANCE: Float = 100.0f
    private const val MIN_RENDER_TARGET_SIZE: Int = 1
    private const val PAGE_CHANGE_ROTATION_MULTIPLIER: Float = 7.0f
    private const val PAGE_CHANGING_DAMPING: Float = 1.5f
    private const val SCALED_HEIGHT: Int = 2304
    private const val SCALED_WIDTH: Int = 1152
    private const val STARS_MOTION_INTENSITY: Float = 0.12f
  }

  class Factory
  @Inject
  constructor(
    private val context: Context,
    private val cloudsProviderFactory: CloudsProvider.Factory,
    private val userLocation: UserLocationEarth,
    private val inputMultiplexer: InputMultiplexer,
    private val fpsThrottler: FPSThrottler
  ) {
    fun create(): EarthEngine =
      EarthEngine(context, cloudsProviderFactory, userLocation, inputMultiplexer, fpsThrottler)
  }
}

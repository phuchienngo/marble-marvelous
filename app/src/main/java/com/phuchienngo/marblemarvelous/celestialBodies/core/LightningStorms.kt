package com.phuchienngo.marblemarvelous.celestialBodies.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.FrameBufferCubemap
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.GdxRuntimeException
import com.phuchienngo.marblemarvelous.transforms.Transform
import com.phuchienngo.marblemarvelous.utils.BaseMathUtils
import com.phuchienngo.marblemarvelous.weather.StormProtos
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

class LightningStorms(
    latestStorms: StormProtos.StormLocations?,
) : Mesh(
        false,
        LIGHTNING_STORMS,
        0,
        VertexAttribute.Position(),
        VertexAttribute(SIZE_ID, 1, "a_size"),
        VertexAttribute(OPACITY_ID, 1, "a_opacity"),
        VertexAttribute(LIFE_ID, 1, "a_life"),
    ) {
    private val positions =
        arrayOf(
            Vector2(41.0f, RADIUS_SPHERE),
            Vector2(34.0f, -118.0f),
            Vector2(-34.0f, -58.0f),
            Vector2(-45.99282f, 166.55858f),
            Vector2(77.0f, 104.0f),
            Vector2(35.0f, 139.0f),
        )
    private var positionsDebug = 0
    private var stormsLocations: StormProtos.StormLocations? = null
    private val mCamera = PerspectiveCamera(90.0f, CUBEMAP_SIZE.toFloat(), CUBEMAP_SIZE.toFloat())
    private var shader: ShaderProgram? = null

    @JvmField var transform = Transform()
    private val stormVertices = FloatArray(1500)
    private val fbo = FrameBufferCubemap(Pixmap.Format.RGBA8888, CUBEMAP_SIZE, CUBEMAP_SIZE, false)

    init {
        setStormsLocations(latestStorms)
        mCamera.position.set(0.0f, 0.0f, 0.0f)
        mCamera.lookAt(0.0f, 0.0f, -1.0f)
        mCamera.near = 0.1f
        mCamera.far = 1000.0f
        mCamera.update()
        shader =
            ShaderProgram(
                Gdx.files.internal("earth/storms.vert").readString(),
                Gdx.files.internal("earth/storms.frag").readString(),
            )
        if (!shader!!.isCompiled) throw GdxRuntimeException(shader!!.log)
        fbo.colorBufferTexture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear)
        initPositions()
    }

    fun begin() {
        mCamera.position.set(0.0f, 0.0f, 0.0f)
        mCamera.lookAt(0.0f, 0.0f, -1.0f)
        mCamera.near = 0.1f
        mCamera.far = 1000.0f
        mCamera.update()
        transform.update()
        fbo.begin()
        shader!!.bind()
    }

    fun end() {
        fbo.end()
    }

    fun render() {
        while (fbo.nextSide()) {
            fbo.side.getUp(mCamera.up)
            fbo.side.getDirection(mCamera.direction)
            mCamera.update()
            shader!!.setUniformMatrix("u_projectionMatrix", mCamera.projection)
            shader!!.setUniformMatrix("u_viewMatrix", mCamera.view)
            Gdx.gl.glEnable(GL20.GL_BLEND)
            Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
            Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            Gdx.gl.glClear(16640)
            super.render(shader, 0)
            Gdx.gl.glDisable(GL20.GL_BLEND)
        }
        Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
    }

    fun update(delta: Float) {
        updatePositions(delta)
        begin()
        render()
        end()
    }

    override fun dispose() {
        super.dispose()
        fbo.dispose()
        shader?.let {
            it.dispose()
            shader = null
        }
    }

    fun getStormsTexture(): Cubemap = fbo.colorBufferTexture

    private fun initPositions() {
        var i = 0
        while (i < stormVertices.size) {
            updateStormPosition(stormVertices, i)
            stormVertices[i + 4] = Random.nextDouble().toFloat()
            i += 6
        }
        setVertices(stormVertices)
    }

    private fun updatePositions(delta: Float) {
        var i = 0
        while (i < stormVertices.size) {
            if (stormVertices[i + 5] < 0.0f) {
                updateStormPosition(stormVertices, i)
                stormVertices[i + 5] = BaseMathUtils.randomMapped(LIFE_TIME_SECONDS, MAX_DURATION_SECONDS)
            }
            val realLifeParticle = min(stormVertices[i + 5] / LIFE_TIME_SECONDS, 1.0f)
            stormVertices[i + 4] = sin(3.141592653589793 * realLifeParticle.toDouble()).toFloat()
            stormVertices[i + 4] = stormVertices[i + 4] * stormVertices[i + 4]
            stormVertices[i + 5] = stormVertices[i + 5] - delta
            i += 6
        }
        updateVertices(0, stormVertices)
    }

    private fun updateStormPosition(
        vertices: FloatArray,
        i: Int,
    ) {
        val lat: Float
        val lng: Float
        val locs = stormsLocations
        if (locs != null && locs.locationsList.size > 0) {
            val locationsList = locs.locationsList
            val latLongIdx = (Random.nextDouble() * (locationsList.size - 1).toDouble()).roundToInt()
            val lng2 = locationsList[latLongIdx].lngDeg
            val lat2 = locationsList[latLongIdx].latDeg
            lng = lng2 * 0.017453292f
            lat = 0.017453292f * lat2
        } else {
            lat = BaseMathUtils.randomMapped(-1.5707964f, 1.5707964f)
            lng = BaseMathUtils.randomMapped(0.0f, 6.2831855f)
        }
        vertices[i] = (cos(lng.toDouble()) * 2.0 * cos(lat.toDouble())).toFloat()
        vertices[i + 1] = (sin(lng.toDouble()) * 2.0 * cos(lat.toDouble())).toFloat()
        vertices[i + 2] = (2.0 * sin(lat.toDouble())).toFloat()
        vertices[i + 3] = BaseMathUtils.randomMapped(MIN_SIZE, MAX_SIZE)
        vertices[i + 5] = BaseMathUtils.randomMapped(LIFE_TIME_SECONDS, MAX_DURATION_SECONDS)
    }

    fun setStormsLocations(stormsLocations: StormProtos.StormLocations?) {
        this.stormsLocations = stormsLocations
    }

    companion object {
        private const val CUBEMAP_SIZE = 256
        private const val LIFE_ID = 20
        private const val LIFE_TIME_SECONDS = 0.5f
        private const val LIGHTNING_STORMS = 250
        private const val MAX_DURATION_SECONDS = 5.0f
        private const val MAX_SIZE = 4.0f
        private const val MIN_SIZE = 3.0f
        private const val OPACITY_ID = 10
        private const val RADIUS_SPHERE = 2.0f
        private const val SIZE_ID = 17
    }
}

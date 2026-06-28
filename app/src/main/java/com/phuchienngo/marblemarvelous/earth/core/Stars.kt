package com.phuchienngo.marblemarvelous.earth.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector3
import com.phuchienngo.marblemarvelous.transforms.Transform
import com.phuchienngo.marblemarvelous.utils.ShaderUtils
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class Stars(
    camera: PerspectiveCamera?,
) : Mesh(
        true,
        STARS_NUMBER,
        0,
        VertexAttribute.Position(),
        VertexAttribute(SIZE_ID, 1, "a_size"),
        VertexAttribute(OPACITY_ID, 1, "a_opacity"),
    ) {
    private var shader: ShaderProgram? = ShaderUtils.load("marble/stars/stars")

    @JvmField var transform = Transform()
    private val inc = Vector3()
    private val min = Vector3()
    private val normal = Vector3()
    private var mAod = 0.0f

    init {
        updateStarsPositions(camera)
    }

    fun begin(camera: Camera) {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)
        shader!!.bind()
        shader!!.setUniformMatrix("u_projectionMatrix", camera.projection)
        shader!!.setUniformf("u_main_opacity", 1.0f - mAod)
    }

    fun end() {
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    fun render() {
        transform.update()
        shader!!.setUniformMatrix("u_worldMatrix", transform)
        super.render(shader, 0)
    }

    private fun random(n: Float): Float {
        val value = sin(n.toDouble()) * 43758.5453123
        return (value - floor(value)).toFloat()
    }

    override fun dispose() {
        super.dispose()
        shader?.let {
            it.dispose()
            shader = null
        }
    }

    fun updateStarsPositions(camera: PerspectiveCamera?) {
        val vertices = FloatArray(750)
        setCameraLimits(camera ?: PerspectiveCamera())
        val v = Vector3()
        var i = 0
        while (i < vertices.size) {
            val x = (Random.nextDouble().toFloat() * inc.x) + min.x
            val y = (Random.nextDouble().toFloat() * inc.y) + min.y
            val z = (Random.nextDouble().toFloat() * inc.z) + min.z
            v.set(x, y, z)
            v.add(normal.cpy().scl((5.0 + (Random.nextDouble() * 50.0)).toFloat()))
            vertices[i] = v.x
            vertices[i + 1] = v.y
            vertices[i + 2] = v.z
            val seed = v.x + v.y + v.z
            val frame = floor(((random(2.0f * seed) * 3.0f) + 0.5f).toDouble())
            vertices[i + 3] = ((1.0f + (3.0f * random(8.0f * seed))).toDouble() + floor(abs(frame - 1.5).toDouble())).toFloat()
            vertices[i + 4] = 0.6f - (0.5f * random(4.0f * seed))
            i += 5
        }
        setVertices(vertices)
    }

    private fun setCameraLimits(camera: PerspectiveCamera) {
        val p = camera.frustum.planes[1]
        val p1 = camera.frustum.planePoints[4]
        val p2 = camera.frustum.planePoints[5]
        val p3 = camera.frustum.planePoints[6]
        val p4 = camera.frustum.planePoints[7]
        val farNormal = p.normal
        val xMin = min(p1.x, min(p2.x, min(p3.x, p4.x)))
        val xMax = max(p1.x, max(p2.x, max(p3.x, p4.x)))
        val yMin = min(p1.y, min(p2.y, min(p3.y, p4.y)))
        val yMax = max(p1.y, max(p2.y, max(p3.y, p4.y)))
        val zMin = min(p1.z, min(p2.z, min(p3.z, p4.z)))
        val zMax = max(p1.z, max(p2.z, max(p3.z, p4.z)))
        inc.set(xMax - xMin, yMax - yMin, zMax - zMin)
        min.set(xMin, yMin, zMin)
        normal.set(farNormal)
    }

    fun setAOD(aodValue: Float) {
        mAod = aodValue
    }

    companion object {
        private const val OPACITY_ID = 16
        private const val SIZE_ID = 15
        private const val STARS_NUMBER = 150
    }
}

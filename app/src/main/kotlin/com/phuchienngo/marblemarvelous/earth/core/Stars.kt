package com.phuchienngo.marblemarvelous.earth.core

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Camera
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.PerspectiveCamera
import com.badlogic.gdx.graphics.VertexAttribute
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Plane
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
    camera: PerspectiveCamera?
) : Mesh(
        true,
        STARS_NUMBER,
        0,
        VertexAttribute.Position(),
        VertexAttribute(SIZE_ID, 1, "a_size"),
        VertexAttribute(OPACITY_ID, 1, "a_opacity")
    ) {
    private var shader: ShaderProgram? = ShaderUtils.load("marble/stars/stars")

    @JvmField var transform = Transform()
    private val inc: Vector3 = Vector3()
    private val min: Vector3 = Vector3()
    private val normal: Vector3 = Vector3()
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
        val value: Double = sin(n.toDouble()) * 43758.5453123
        return (value - floor(value)).toFloat()
    }

    override fun dispose() {
        super.dispose()
        val currentShader: ShaderProgram? = shader
        if (currentShader != null) {
            currentShader.dispose()
            shader = null
        }
    }

    fun updateStarsPositions(camera: PerspectiveCamera?) {
        val vertices: FloatArray = FloatArray(750)
        setCameraLimits(camera ?: PerspectiveCamera())
        val v: Vector3 = Vector3()
        var i = 0
        while (i < vertices.size) {
            val x: Float = (Random.nextDouble().toFloat() * inc.x) + min.x
            val y: Float = (Random.nextDouble().toFloat() * inc.y) + min.y
            val z: Float = (Random.nextDouble().toFloat() * inc.z) + min.z
            v.set(x, y, z)
            v.add(normal.cpy().scl((5.0 + (Random.nextDouble() * 50.0)).toFloat()))
            vertices[i] = v.x
            vertices[i + 1] = v.y
            vertices[i + 2] = v.z
            val seed: Float = v.x + v.y + v.z
            val frame: Double = floor(((random(2.0f * seed) * 3.0f) + 0.5f).toDouble())
            vertices[i + 3] = ((1.0f + (3.0f * random(8.0f * seed))).toDouble() + floor(abs(frame - 1.5).toDouble())).toFloat()
            vertices[i + 4] = 0.6f - (0.5f * random(4.0f * seed))
            i += 5
        }
        setVertices(vertices)
    }

    private fun setCameraLimits(camera: PerspectiveCamera) {
        val p: Plane = camera.frustum.planes[1]
        val p1: Vector3 = camera.frustum.planePoints[4]
        val p2: Vector3 = camera.frustum.planePoints[5]
        val p3: Vector3 = camera.frustum.planePoints[6]
        val p4: Vector3 = camera.frustum.planePoints[7]
        val farNormal: Vector3 = p.normal
        val xMin: Float = min(p1.x, min(p2.x, min(p3.x, p4.x)))
        val xMax: Float = max(p1.x, max(p2.x, max(p3.x, p4.x)))
        val yMin: Float = min(p1.y, min(p2.y, min(p3.y, p4.y)))
        val yMax: Float = max(p1.y, max(p2.y, max(p3.y, p4.y)))
        val zMin: Float = min(p1.z, min(p2.z, min(p3.z, p4.z)))
        val zMax: Float = max(p1.z, max(p2.z, max(p3.z, p4.z)))
        inc.set(xMax - xMin, yMax - yMin, zMax - zMin)
        min.set(xMin, yMin, zMin)
        normal.set(farNormal)
    }

    fun setAOD(aodValue: Float) {
        mAod = aodValue
    }

    companion object {
        private const val OPACITY_ID: Int = 16
        private const val SIZE_ID: Int = 15
        private const val STARS_NUMBER: Int = 150
    }
}

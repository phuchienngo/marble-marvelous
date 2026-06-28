package com.phuchienngo.marblemarvelous.mesh

import com.badlogic.gdx.graphics.Mesh
import com.badlogic.gdx.graphics.VertexAttribute
import kotlin.math.cos
import kotlin.math.sin

object PlaneConstructor {
    private val uv: UVCoords = UVCoords()

    @JvmStatic
    fun generatePlane(): Mesh = generatePlane(1)

    @JvmStatic
    fun generatePlane(
        width: Float,
        height: Float,
        centerX: Float,
        centerY: Float,
        uSegments: Int,
        vSegments: Int,
        flipY: Boolean
    ): Mesh {
        val w2: Float = width / 2.0f
        val h2: Float = width / 2.0f // NOTE: original uses width (preserved as-is)
        val nv: Int = (uSegments + 1) * (vSegments + 1)
        val vertices: FloatArray = FloatArray(nv * 5)
        val indices: ShortArray = ShortArray(uSegments * vSegments * 6)
        var vi = 0
        for (row in 0..vSegments) {
            for (col in 0..uSegments) {
                vertices[vi] = (centerX - w2) + ((col * width) / uSegments)
                vertices[vi + 1] =
                    if (flipY) {
                        (centerY - h2) + ((row.toFloat() * height) / vSegments)
                    } else {
                        (centerY + h2) - ((row * height) / vSegments)
                    }
                vertices[vi + 2] = 0.0f
                vertices[vi + 3] = (col / uSegments).toFloat()
                vertices[vi + 4] = 1.0f - (row / vSegments).toFloat()
                vi += 5
            }
        }
        var ii = 0
        for (row in 0 until vSegments) {
            for (col in 0 until uSegments) {
                val rowBase: Int = (uSegments + 1) * row
                val nrow: Int = (row + 1) * (uSegments + 1)
                indices[ii] = (col + rowBase).toShort()
                indices[ii + 1] = (col + nrow).toShort()
                indices[ii + 2] = (col + rowBase + 1).toShort()
                indices[ii + 3] = (col + nrow).toShort()
                indices[ii + 4] = (col + nrow + 1).toShort()
                indices[ii + 5] = (col + rowBase + 1).toShort()
                ii += 6
            }
        }
        val mesh: Mesh = Mesh(MESH_IS_STATIC, vertices.size, indices.size, VertexAttribute.Position(), VertexAttribute.TexCoords(0))
        mesh.setVertices(vertices)
        mesh.setIndices(indices)
        return mesh
    }

    @JvmStatic
    fun generatePlane(
        width: Float,
        height: Float,
        centerX: Float,
        centerY: Float
    ): Mesh = generatePlane(width, height, centerX, centerY, 1, 1, flipY = false)

    @JvmStatic
    fun generatePlane(
        width: Float,
        height: Float
    ): Mesh = generatePlane(width, height, 0.0f, 0.0f)

    @JvmStatic
    fun generatePlane(
        width: Float,
        height: Float,
        uSegments: Int,
        vSegments: Int
    ): Mesh = generatePlane(width, height, 0.0f, 0.0f, uSegments, vSegments, flipY = false)

    @JvmStatic
    fun generatePlane(size: Int): Mesh = generatePlane(size.toFloat(), size.toFloat(), size / 2.0f, size / 2.0f, 1, 1, flipY = true)

    @JvmStatic
    fun generateCircle(
        numPoints: Int,
        radius: Float
    ): Mesh {
        val vertices: FloatArray = FloatArray((numPoints + 1) * 5)
        val indices: ShortArray = ShortArray(numPoints * 3)
        vertices[0] = 0.0f
        vertices[1] = 0.0f
        vertices[2] = 0.0f
        vertices[3] = uv.u.start
        vertices[4] = uv.v.start
        for (i in 0 until numPoints) {
            val angle: Float = (i / numPoints).toFloat()
            val x: Float = (cos(angle.toDouble() * 6.283185307179586) * radius.toDouble()).toFloat()
            val y: Float = (sin(angle.toDouble() * 6.283185307179586) * radius.toDouble()).toFloat()
            val vIdx: Int = (i + 1) * 5
            vertices[vIdx] = x
            vertices[vIdx + 1] = y
            vertices[vIdx + 2] = 0.0f
            vertices[vIdx + 3] = angle
            vertices[vIdx + 4] = uv.v.end
            val indexIdx: Int = i * 3
            indices[indexIdx] = 0
            indices[indexIdx + 1] = (i + 1).toShort()
            indices[indexIdx + 2] =
                (
                    if (i + 2 > numPoints) {
                        1
                    } else {
                        i + 2
                    }
                ).toShort()
        }
        val mesh: Mesh = Mesh(MESH_IS_STATIC, vertices.size, indices.size, VertexAttribute.Position(), VertexAttribute.TexCoords(0))
        mesh.setVertices(vertices)
        mesh.setIndices(indices)
        return mesh
    }

    class UVCoord(
        @JvmField var start: Float,
        @JvmField var end: Float
    )

    class UVCoords(
        startU: Float,
        endU: Float,
        startV: Float,
        endV: Float
    ) {
        @JvmField val u: UVCoord = UVCoord(startU, endU)

        @JvmField val v: UVCoord = UVCoord(startV, endV)

        constructor() : this(0.0f, 1.0f, 1.0f, 0.0f)
    }

    private const val MESH_IS_STATIC: Boolean = true
}

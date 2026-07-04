package com.phuchienngo.marblemarvelous.filament

import android.content.Context
import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import com.phuchienngo.marblemarvelous.math.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * A camera-facing moon disc. Its screen position is fixed in the upper sky (the
 * narrow field of view makes a true-direction moon almost always off-screen or
 * behind Earth), but the shading is driven by the real lunar phase.
 */
internal class FilamentMoon private constructor(
  val entity: Int,
  private val material: Material,
  private val materialInstance: MaterialInstance,
  private val vertexBuffer: VertexBuffer,
  private val indexBuffer: IndexBuffer
) {
  private val vertices: FloatArray = FloatArray(VERTICES * FLOATS_PER_VERTEX)
  private val uploadBuffer: FloatBuffer =
    ByteBuffer
      .allocateDirect(vertices.size * FLOAT_BYTES)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()

  fun update(
    engine: Engine,
    cameraPosition: Vec3,
    cameraTarget: Vec3,
    cameraUp: Vec3
  ) {
    val forward: Vec3 = cameraTarget.add(cameraPosition.scale(-1.0f)).normalized()
    val right: Vec3 = forward.cross(cameraUp).normalized()
    val up: Vec3 = right.cross(forward).normalized()
    val center: Vec3 =
      cameraPosition
        .add(forward.scale(VIEW_DISTANCE))
        .add(up.scale(UP_OFFSET))
        .add(right.scale(RIGHT_OFFSET))
    var offset = 0
    offset = writeCorner(offset, center.add(right.scale(-RADIUS)).add(up.scale(-RADIUS)), -1.0f, -1.0f)
    offset = writeCorner(offset, center.add(right.scale(RADIUS)).add(up.scale(-RADIUS)), 1.0f, -1.0f)
    offset = writeCorner(offset, center.add(right.scale(RADIUS)).add(up.scale(RADIUS)), 1.0f, 1.0f)
    offset = writeCorner(offset, center.add(right.scale(-RADIUS)).add(up.scale(RADIUS)), -1.0f, 1.0f)
    uploadBuffer.clear()
    uploadBuffer.put(vertices)
    uploadBuffer.flip()
    vertexBuffer.setBufferAt(engine, VERTEX_BUFFER_INDEX, uploadBuffer)
  }

  fun setPhaseLight(light: FloatArray) {
    materialInstance.setParameter(MOON_LIGHT, light[0], light[1], light[2])
  }

  fun destroy(
    engine: Engine,
    entityManager: EntityManager
  ) {
    engine.destroyEntity(entity)
    entityManager.destroy(entity)
    engine.destroyVertexBuffer(vertexBuffer)
    engine.destroyIndexBuffer(indexBuffer)
    engine.destroyMaterialInstance(materialInstance)
    engine.destroyMaterial(material)
  }

  private fun writeCorner(
    startOffset: Int,
    position: Vec3,
    u: Float,
    v: Float
  ): Int {
    vertices[startOffset] = position.x
    vertices[startOffset + 1] = position.y
    vertices[startOffset + 2] = position.z
    vertices[startOffset + 3] = u
    vertices[startOffset + 4] = v
    vertices[startOffset + 5] = 0.0f
    vertices[startOffset + 6] = 0.0f
    return startOffset + FLOATS_PER_VERTEX
  }

  companion object {
    fun create(
      context: Context,
      engine: Engine,
      entityManager: EntityManager
    ): FilamentMoon {
      val material: Material = FilamentMaterialLoader.load(context, engine, MATERIAL_ASSET_PATH)
      val materialInstance: MaterialInstance = material.createInstance()
      val vertexBuffer: VertexBuffer =
        VertexBuffer
          .Builder()
          .bufferCount(VERTEX_BUFFER_COUNT)
          .vertexCount(VERTICES)
          .attribute(
            VertexBuffer.VertexAttribute.POSITION,
            VERTEX_BUFFER_INDEX,
            VertexBuffer.AttributeType.FLOAT3,
            POSITION_OFFSET_BYTES,
            VERTEX_STRIDE_BYTES
          )
          .attribute(
            VertexBuffer.VertexAttribute.CUSTOM0,
            VERTEX_BUFFER_INDEX,
            VertexBuffer.AttributeType.FLOAT4,
            CUSTOM0_OFFSET_BYTES,
            VERTEX_STRIDE_BYTES
          )
          .build(engine)
      val indexBuffer: IndexBuffer =
        IndexBuffer
          .Builder()
          .indexCount(INDEX_COUNT)
          .bufferType(IndexBuffer.Builder.IndexType.USHORT)
          .build(engine)
      indexBuffer.setBuffer(engine, directShortBuffer(shortArrayOf(0, 1, 2, 0, 2, 3)))
      val entity: Int = entityManager.create()
      RenderableManager
        .Builder(RENDERABLE_COUNT)
        .geometry(
          PRIMITIVE_INDEX,
          RenderableManager.PrimitiveType.TRIANGLES,
          vertexBuffer,
          indexBuffer,
          0,
          INDEX_COUNT
        )
        .material(PRIMITIVE_INDEX, materialInstance)
        .boundingBox(Box(0.0f, 0.0f, 0.0f, BOUNDING_EXTENT, BOUNDING_EXTENT, BOUNDING_EXTENT))
        .culling(false)
        .castShadows(false)
        .receiveShadows(false)
        .build(engine, entity)
      return FilamentMoon(
        entity = entity,
        material = material,
        materialInstance = materialInstance,
        vertexBuffer = vertexBuffer,
        indexBuffer = indexBuffer
      )
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

    const val MATERIAL_ASSET_PATH: String = "filament/moon.filamat"
    private const val MOON_LIGHT: String = "moonLight"
    private const val VERTICES: Int = 4
    private const val INDEX_COUNT: Int = 6
    private const val FLOAT_BYTES: Int = 4
    private const val SHORT_BYTES: Int = 2
    private const val FLOATS_PER_VERTEX: Int = 7
    private const val POSITION_OFFSET_BYTES: Int = 0
    private const val CUSTOM0_OFFSET_BYTES: Int = 12
    private const val VERTEX_STRIDE_BYTES: Int = FLOATS_PER_VERTEX * FLOAT_BYTES
    private const val VERTEX_BUFFER_COUNT: Int = 1
    private const val VERTEX_BUFFER_INDEX: Int = 0
    private const val PRIMITIVE_INDEX: Int = 0
    private const val RENDERABLE_COUNT: Int = 1
    private const val BOUNDING_EXTENT: Float = 100.0f

    // Placement in the camera's view: distance in front, and offsets up/right so
    // the moon clears the Earth disc and sits in the upper sky.
    private const val VIEW_DISTANCE: Float = 14.0f
    private const val UP_OFFSET: Float = 1.5f
    private const val RIGHT_OFFSET: Float = 0.5f
    private const val RADIUS: Float = 0.28f
  }
}

package com.phuchienngo.marblemarvelous.filament

import com.google.android.filament.Box
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import com.google.android.filament.filamat.MaterialBuilder
import com.phuchienngo.marblemarvelous.math.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.tan
import kotlin.random.Random

internal class FilamentStars private constructor(
  val entity: Int,
  private val material: Material,
  private val materialInstance: MaterialInstance,
  private val vertexBuffer: VertexBuffer,
  private val indexBuffer: IndexBuffer,
  private val seeds: List<StarSeed>
) {
  fun update(
    engine: Engine,
    cameraPosition: Vec3,
    cameraTarget: Vec3,
    cameraUp: Vec3,
    aspectRatio: Float,
    verticalFovDegrees: Float
  ) {
    val forward: Vec3 =
      cameraTarget
        .add(cameraPosition.scale(-1.0f))
        .normalized()
    val right: Vec3 = forward.cross(cameraUp).normalized()
    val up: Vec3 = right.cross(forward).normalized()
    val distance: Float = STAR_PLANE_DISTANCE
    val halfHeight: Float =
      (tan(Math.toRadians((verticalFovDegrees * 0.5f).toDouble())) * distance).toFloat()
    val halfWidth: Float = halfHeight * aspectRatio
    val center: Vec3 = cameraPosition.add(forward.scale(distance))
    val vertices = FloatArray(STAR_COUNT * VERTICES_PER_STAR * FLOATS_PER_VERTEX)
    var offset = 0
    for (seed in seeds) {
      val starCenter: Vec3 =
        center
          .add(right.scale(seed.x * halfWidth * STAR_PLANE_FILL))
          .add(up.scale(seed.y * halfHeight * STAR_PLANE_FILL))
      val size: Float = seed.size
      val color = floatArrayOf(
        seed.brightness,
        seed.brightness,
        seed.brightness,
        seed.opacity
      )
      offset = writeVertex(vertices, offset, starCenter.add(right.scale(-size)).add(up.scale(-size)), color)
      offset = writeVertex(vertices, offset, starCenter.add(right.scale(size)).add(up.scale(-size)), color)
      offset = writeVertex(vertices, offset, starCenter.add(right.scale(size)).add(up.scale(size)), color)
      offset = writeVertex(vertices, offset, starCenter.add(right.scale(-size)).add(up.scale(size)), color)
    }
    vertexBuffer.setBufferAt(engine, VERTEX_BUFFER_INDEX, directFloatBuffer(vertices))
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

  private data class StarSeed(
    val x: Float,
    val y: Float,
    val size: Float,
    val brightness: Float,
    val opacity: Float
  )

  companion object {
    fun create(
      engine: Engine,
      entityManager: EntityManager
    ): FilamentStars {
      val material: Material = createMaterial(engine)
      val materialInstance: MaterialInstance = material.createInstance()
      val vertexBuffer: VertexBuffer =
        VertexBuffer
          .Builder()
          .bufferCount(VERTEX_BUFFER_COUNT)
          .vertexCount(STAR_COUNT * VERTICES_PER_STAR)
          .attribute(
            VertexBuffer.VertexAttribute.POSITION,
            VERTEX_BUFFER_INDEX,
            VertexBuffer.AttributeType.FLOAT3,
            POSITION_OFFSET_BYTES,
            VERTEX_STRIDE_BYTES
          )
          .attribute(
            VertexBuffer.VertexAttribute.COLOR,
            VERTEX_BUFFER_INDEX,
            VertexBuffer.AttributeType.FLOAT4,
            COLOR_OFFSET_BYTES,
            VERTEX_STRIDE_BYTES
          )
          .build(engine)
      val indexBuffer: IndexBuffer =
        IndexBuffer
          .Builder()
          .indexCount(STAR_COUNT * INDICES_PER_STAR)
          .bufferType(IndexBuffer.Builder.IndexType.USHORT)
          .build(engine)
      indexBuffer.setBuffer(engine, directShortBuffer(createIndices()))
      val entity: Int = entityManager.create()
      RenderableManager
        .Builder(RENDERABLE_COUNT)
        .geometry(
          PRIMITIVE_INDEX,
          RenderableManager.PrimitiveType.TRIANGLES,
          vertexBuffer,
          indexBuffer,
          0,
          STAR_COUNT * INDICES_PER_STAR
        )
        .material(PRIMITIVE_INDEX, materialInstance)
        .boundingBox(Box(0.0f, 0.0f, 0.0f, STAR_PLANE_DISTANCE, STAR_PLANE_DISTANCE, STAR_PLANE_DISTANCE))
        .culling(false)
        .castShadows(false)
        .receiveShadows(false)
        .build(engine, entity)
      return FilamentStars(
        entity = entity,
        material = material,
        materialInstance = materialInstance,
        vertexBuffer = vertexBuffer,
        indexBuffer = indexBuffer,
        seeds = createSeeds()
      )
    }

    private fun createMaterial(engine: Engine): Material {
      FilamentRuntime.initialize()
      val materialPackage =
        MaterialBuilder()
          .name(MATERIAL_NAME)
          .shading(MaterialBuilder.Shading.UNLIT)
          .require(MaterialBuilder.VertexAttribute.COLOR)
          .blending(MaterialBuilder.BlendingMode.TRANSPARENT)
          .culling(MaterialBuilder.CullingMode.NONE)
          .depthWrite(false)
          .depthCulling(true)
          .targetApi(MaterialBuilder.TargetApi.VULKAN)
          .platform(MaterialBuilder.Platform.MOBILE)
          .optimization(MaterialBuilder.Optimization.PERFORMANCE)
          .material(
            """
            void material(inout MaterialInputs material) {
              prepareMaterial(material);
              material.baseColor = getColor();
            }
            """.trimIndent()
          )
          .build()
      require(materialPackage.isValid) {
        "Filament stars material package is invalid"
      }
      val payload = materialPackage.buffer
      return Material
        .Builder()
        .payload(payload, payload.remaining())
        .build(engine)
    }

    private fun createSeeds(): List<StarSeed> {
      val random = Random(STAR_SEED)
      val seeds = ArrayList<StarSeed>(STAR_COUNT)
      var starIndex = 0
      while (starIndex < STAR_COUNT) {
        seeds.add(
          StarSeed(
            x = random.nextFloat() * 2.0f - 1.0f,
            y = random.nextFloat() * 2.0f - 1.0f,
            size = STAR_MIN_SIZE + random.nextFloat() * (STAR_MAX_SIZE - STAR_MIN_SIZE),
            brightness = STAR_MIN_BRIGHTNESS + random.nextFloat() * (STAR_MAX_BRIGHTNESS - STAR_MIN_BRIGHTNESS),
            opacity = STAR_MIN_OPACITY + random.nextFloat() * (STAR_MAX_OPACITY - STAR_MIN_OPACITY)
          )
        )
        starIndex++
      }
      return seeds
    }

    private fun createIndices(): ShortArray {
      val indices = ShortArray(STAR_COUNT * INDICES_PER_STAR)
      var offset = 0
      for (starIndex in 0 until STAR_COUNT) {
        val vertexOffset: Short = (starIndex * VERTICES_PER_STAR).toShort()
        indices[offset++] = vertexOffset
        indices[offset++] = (vertexOffset + 1).toShort()
        indices[offset++] = (vertexOffset + 2).toShort()
        indices[offset++] = vertexOffset
        indices[offset++] = (vertexOffset + 2).toShort()
        indices[offset++] = (vertexOffset + 3).toShort()
      }
      return indices
    }

    private fun writeVertex(
      vertices: FloatArray,
      startOffset: Int,
      position: Vec3,
      color: FloatArray
    ): Int {
      var offset = startOffset
      vertices[offset++] = position.x
      vertices[offset++] = position.y
      vertices[offset++] = position.z
      vertices[offset++] = color[0]
      vertices[offset++] = color[1]
      vertices[offset++] = color[2]
      vertices[offset++] = color[3]
      return offset
    }

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

    private const val COLOR_OFFSET_BYTES: Int = 12
    private const val FLOAT_BYTES: Int = 4
    private const val FLOATS_PER_VERTEX: Int = 7
    private const val INDICES_PER_STAR: Int = 6
    private const val MATERIAL_NAME: String = "FilamentStars"
    private const val POSITION_OFFSET_BYTES: Int = 0
    private const val PRIMITIVE_INDEX: Int = 0
    private const val RENDERABLE_COUNT: Int = 1
    private const val SHORT_BYTES: Int = 2
    private const val STAR_COUNT: Int = 150
    private const val STAR_MAX_BRIGHTNESS: Float = 0.86f
    private const val STAR_MAX_OPACITY: Float = 0.46f
    private const val STAR_MAX_SIZE: Float = 0.010f
    private const val STAR_MIN_BRIGHTNESS: Float = 0.52f
    private const val STAR_MIN_OPACITY: Float = 0.16f
    private const val STAR_MIN_SIZE: Float = 0.0035f
    private const val STAR_PLANE_DISTANCE: Float = 18.0f
    private const val STAR_PLANE_FILL: Float = 0.96f
    private const val STAR_SEED: Int = 1701
    private const val VERTEX_BUFFER_COUNT: Int = 1
    private const val VERTEX_BUFFER_INDEX: Int = 0
    private const val VERTEX_STRIDE_BYTES: Int = FLOATS_PER_VERTEX * FLOAT_BYTES
    private const val VERTICES_PER_STAR: Int = 4
  }
}

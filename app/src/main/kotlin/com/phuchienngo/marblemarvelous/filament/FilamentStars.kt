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
  // Interleaved position + color + custom0 (twinkle params) vertex data. Rebuilt
  // only when the camera changes; the twinkle itself runs in the shader.
  private val vertices: FloatArray =
    FloatArray(STAR_COUNT * VERTICES_PER_STAR * FLOATS_PER_VERTEX)
  private val uploadBuffer: FloatBuffer =
    ByteBuffer
      .allocateDirect(vertices.size * FLOAT_BYTES)
      .order(ByteOrder.nativeOrder())
      .asFloatBuffer()

  /**
   * Rebuilds the camera-facing star quads (position + base color + per-star
   * twinkle params) and uploads them. Only needed when the camera changes.
   */
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
    var offset = 0
    for (seed in seeds) {
      val starCenter: Vec3 =
        center
          .add(right.scale(seed.x * halfWidth * STAR_PLANE_FILL))
          .add(up.scale(seed.y * halfHeight * STAR_PLANE_FILL))
      val size: Float = seed.size
      offset = writeVertex(vertices, offset, starCenter.add(right.scale(-size)).add(up.scale(-size)), seed)
      offset = writeVertex(vertices, offset, starCenter.add(right.scale(size)).add(up.scale(-size)), seed)
      offset = writeVertex(vertices, offset, starCenter.add(right.scale(size)).add(up.scale(size)), seed)
      offset = writeVertex(vertices, offset, starCenter.add(right.scale(-size)).add(up.scale(size)), seed)
    }
    uploadBuffer.clear()
    uploadBuffer.put(vertices)
    uploadBuffer.flip()
    vertexBuffer.setBufferAt(engine, VERTEX_BUFFER_INDEX, uploadBuffer)
  }

  /**
   * Advances the shader twinkle animation. Cheap: sets a single float uniform,
   * no vertex upload. Call once per rendered frame.
   */
  fun setTime(elapsedSeconds: Float) {
    materialInstance.setParameter(TIME_PARAM, elapsedSeconds)
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
    val opacity: Float,
    val twinklePhase: Float,
    val twinkleSpeed: Float,
    val twinkleAmount: Float
  )

  companion object {
    fun create(
      context: Context,
      engine: Engine,
      entityManager: EntityManager
    ): FilamentStars {
      val material: Material = createMaterial(context, engine)
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

    private fun createMaterial(
      context: Context,
      engine: Engine
    ): Material = FilamentMaterialLoader.load(context, engine, MATERIAL_ASSET_PATH)

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
            opacity = STAR_MIN_OPACITY + random.nextFloat() * (STAR_MAX_OPACITY - STAR_MIN_OPACITY),
            twinklePhase = random.nextFloat() * TWO_PI,
            twinkleSpeed = TWINKLE_SPEED_MIN + random.nextFloat() * (TWINKLE_SPEED_MAX - TWINKLE_SPEED_MIN),
            twinkleAmount = TWINKLE_AMOUNT_MIN + random.nextFloat() * (TWINKLE_AMOUNT_MAX - TWINKLE_AMOUNT_MIN)
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
      seed: StarSeed
    ): Int {
      vertices[startOffset] = position.x
      vertices[startOffset + 1] = position.y
      vertices[startOffset + 2] = position.z
      vertices[startOffset + 3] = seed.brightness
      vertices[startOffset + 4] = seed.brightness
      vertices[startOffset + 5] = seed.brightness
      vertices[startOffset + 6] = seed.opacity
      vertices[startOffset + 7] = seed.twinklePhase
      vertices[startOffset + 8] = seed.twinkleSpeed
      vertices[startOffset + 9] = seed.twinkleAmount
      vertices[startOffset + 10] = 0.0f
      return startOffset + FLOATS_PER_VERTEX
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
    private const val CUSTOM0_OFFSET_BYTES: Int = 28
    private const val FLOAT_BYTES: Int = 4
    private const val FLOATS_PER_VERTEX: Int = 11
    private const val INDICES_PER_STAR: Int = 6
    const val MATERIAL_ASSET_PATH: String = "filament/stars.filamat"
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
    private const val TIME_PARAM: String = "time"
    private const val TWO_PI: Float = 6.2831855f

    // Twinkle params fed to the shader per star (custom0). Speed sets how fast
    // each star cycles; amount sets how deeply it dims (1.0 = fully off at the
    // trough). The dim/flash sharpness lives in stars.mat.
    private const val TWINKLE_SPEED_MIN: Float = 0.4f
    private const val TWINKLE_SPEED_MAX: Float = 2.1f
    private const val TWINKLE_AMOUNT_MIN: Float = 0.35f
    private const val TWINKLE_AMOUNT_MAX: Float = 1.0f
    private const val VERTEX_BUFFER_COUNT: Int = 1
    private const val VERTEX_BUFFER_INDEX: Int = 0
    private const val VERTEX_STRIDE_BYTES: Int = FLOATS_PER_VERTEX * FLOAT_BYTES
    private const val VERTICES_PER_STAR: Int = 4
  }
}

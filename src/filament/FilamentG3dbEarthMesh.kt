package com.phuchienngo.marblemarvelous.filament

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.InputStream

internal object FilamentG3dbEarthMesh {
  fun load(input: InputStream): FilamentEarthMeshData {
    val root: UbValue = UbJsonSubsetReader(input).parse()
    val mesh: UbObject = requireFirstMesh(root.asObject())
    val layout: VertexLayout = parseVertexLayout(required(mesh, ATTRIBUTES_FIELD))
    val vertexFloats: UbArray = required(mesh, VERTICES_FIELD).asArray()
    require(vertexFloats.size % layout.strideFloats == 0) {
      "earth.g3db vertex data does not match declared attributes"
    }

    val vertexCount: Int = vertexFloats.size / layout.strideFloats
    require(vertexCount <= MAX_UNSIGNED_SHORT_INDEX + 1) {
      "earth.g3db mesh must fit in a 16-bit index buffer"
    }

    val positions = FloatArray(vertexCount * FilamentEarthMeshData.POSITION_COMPONENTS)
    val lookupNormals = FloatArray(vertexCount * FilamentEarthMeshData.LOOKUP_NORMAL_COMPONENTS)
    copyVertices(vertexFloats, layout, vertexCount, positions, lookupNormals)

    return FilamentEarthMeshData(
      positions = positions,
      lookupNormals = lookupNormals,
      indices = parseIndices(mesh, vertexCount)
    )
  }

  private fun copyVertices(
    vertexFloats: UbArray,
    layout: VertexLayout,
    vertexCount: Int,
    positions: FloatArray,
    lookupNormals: FloatArray
  ) {
    for (vertexIndex in 0 until vertexCount) {
      val sourceOffset: Int = vertexIndex * layout.strideFloats
      val positionOffset: Int = vertexIndex * FilamentEarthMeshData.POSITION_COMPONENTS
      val normalOffset: Int = sourceOffset + layout.normalOffset
      val normalTargetOffset: Int = vertexIndex * FilamentEarthMeshData.LOOKUP_NORMAL_COMPONENTS

      positions[positionOffset] = vertexFloats.getFloat(sourceOffset + layout.positionOffset)
      positions[positionOffset + 1] =
        vertexFloats.getFloat(sourceOffset + layout.positionOffset + 1)
      positions[positionOffset + 2] =
        vertexFloats.getFloat(sourceOffset + layout.positionOffset + 2)

      writeLookupNormal(
        lookupNormals = lookupNormals,
        targetOffset = normalTargetOffset,
        x = vertexFloats.getFloat(normalOffset),
        y = vertexFloats.getFloat(normalOffset + 1),
        z = vertexFloats.getFloat(normalOffset + 2)
      )
    }
  }

  private fun parseIndices(
    mesh: UbObject,
    vertexCount: Int
  ): ShortArray {
    val parts: UbArray = required(mesh, PARTS_FIELD).asArray()
    require(parts.size == EXPECTED_PART_COUNT) {
      "earth.g3db must contain exactly one mesh part"
    }

    val part: UbObject = required(parts, PART_INDEX).asObject()
    val indicesJson: UbArray = required(part, INDICES_FIELD).asArray()
    val indices = ShortArray(indicesJson.size)
    for (indexOffset in 0 until indicesJson.size) {
      val index: Int = indicesJson.getInt(indexOffset)
      require(index in 0..<vertexCount) {
        "earth.g3db index is outside the vertex range"
      }
      indices[indexOffset] = index.toShort()
    }
    return indices
  }

  private fun parseVertexLayout(attributes: UbValue): VertexLayout {
    val attributeArray: UbArray = attributes.asArray()
    var strideFloats = 0
    var positionOffset = MISSING_OFFSET
    var normalOffset = MISSING_OFFSET

    for (attributeIndex in 0 until attributeArray.size) {
      when (val attributeName: String = attributeArray.getString(attributeIndex)) {
        POSITION_ATTRIBUTE -> {
          positionOffset = strideFloats
          strideFloats += POSITION_ATTRIBUTE_FLOATS
        }

        NORMAL_ATTRIBUTE -> {
          normalOffset = strideFloats
          strideFloats += NORMAL_ATTRIBUTE_FLOATS
        }

        TEXCOORD0_ATTRIBUTE -> {
          strideFloats += TEXCOORD0_ATTRIBUTE_FLOATS
        }

        else -> error("Unsupported earth.g3db vertex attribute: $attributeName")
      }
    }

    require(positionOffset != MISSING_OFFSET) {
      "earth.g3db must contain POSITION attribute"
    }
    require(normalOffset != MISSING_OFFSET) {
      "earth.g3db must contain NORMAL attribute"
    }

    return VertexLayout(
      strideFloats = strideFloats,
      positionOffset = positionOffset,
      normalOffset = normalOffset
    )
  }

  private fun requireFirstMesh(root: UbObject): UbObject {
    val meshes: UbArray = required(root, MESHES_FIELD).asArray()
    require(meshes.size == EXPECTED_MESH_COUNT) {
      "earth.g3db must contain exactly one mesh"
    }
    return required(meshes, MESH_INDEX).asObject()
  }

  private fun required(
    value: UbObject,
    name: String
  ): UbValue =
    requireNotNull(value.children[name]) {
      "earth.g3db is missing '$name'"
    }

  private fun required(
    value: UbArray,
    index: Int
  ): UbValue =
    value.values.getOrNull(index)
      ?: throw IllegalArgumentException(
        "earth.g3db is missing index $index"
      )

  private fun writeLookupNormal(
    lookupNormals: FloatArray,
    targetOffset: Int,
    x: Float,
    y: Float,
    z: Float
  ) {
    lookupNormals[targetOffset] = x
    lookupNormals[targetOffset + 1] = y
    lookupNormals[targetOffset + 2] = z
    lookupNormals[targetOffset + 3] = 1.0f
  }

  private data class VertexLayout(
    val strideFloats: Int,
    val positionOffset: Int,
    val normalOffset: Int
  )

  private sealed interface UbValue {
    fun asArray(): UbArray = error("earth.g3db value is not an array")

    fun asObject(): UbObject = error("earth.g3db value is not an object")

    fun asString(): String = error("earth.g3db value is not a string")

    fun asFloat(): Float = error("earth.g3db value is not a number")

    fun asInt(): Int = error("earth.g3db value is not a number")
  }

  private data class UbArray(
    val values: List<UbValue>
  ) : UbValue {
    val size: Int
      get() = values.size

    override fun asArray(): UbArray = this

    fun getFloat(index: Int): Float = required(this, index).asFloat()

    fun getInt(index: Int): Int = required(this, index).asInt()

    fun getString(index: Int): String = required(this, index).asString()
  }

  private data class UbObject(
    val children: Map<String, UbValue>
  ) : UbValue {
    override fun asObject(): UbObject = this
  }

  private data class UbString(
    val value: String
  ) : UbValue {
    override fun asString(): String = value
  }

  private data class UbNumber(
    val value: Double
  ) : UbValue {
    override fun asFloat(): Float = value.toFloat()

    override fun asInt(): Int = value.toInt()
  }

  private data object UbNull : UbValue

  private data class UbBoolean(
    val value: Boolean
  ) : UbValue

  private class UbJsonSubsetReader(input: InputStream) {
    private val reader: DataInputStream = DataInputStream(BufferedInputStream(input))

    fun parse(): UbValue = parseValue(reader.readByte())

    private fun parseValue(type: Byte): UbValue =
      when (type) {
        ARRAY_START -> parseArray()
        OBJECT_START -> parseObject()
        NULL_TYPE -> UbNull
        TRUE_TYPE -> UbBoolean(true)
        FALSE_TYPE -> UbBoolean(false)
        BYTE_TYPE,
        UNSIGNED_BYTE_TYPE -> UbNumber(readUnsignedByte().toDouble())

        OLD_SHORT_TYPE -> UbNumber(reader.readShort().toDouble())
        OLD_INT_TYPE -> UbNumber(reader.readInt().toDouble())
        INT_TYPE -> UbNumber(reader.readInt().toDouble())
        LONG_TYPE -> UbNumber(reader.readLong().toDouble())
        FLOAT_TYPE -> UbNumber(reader.readFloat().toDouble())
        DOUBLE_TYPE -> UbNumber(reader.readDouble())
        STRING_TYPE,
        LONG_STRING_TYPE -> UbString(parseString(type))

        SMALL_DATA_TYPE,
        LARGE_DATA_TYPE -> parseData(type)

        else -> error("Unsupported earth.g3db UBJSON type '${type.toInt().toChar()}'")
      }

    private fun parseArray(): UbArray {
      var type: Byte = reader.readByte()
      var valueType: Byte = NO_VALUE_TYPE
      if (type == VALUE_TYPE_MARKER) {
        valueType = reader.readByte()
        type = reader.readByte()
      }

      var expectedSize = UNKNOWN_SIZE
      if (type == SIZE_MARKER) {
        expectedSize = parseSize(reader.readByte()).toInt()
        if (expectedSize == 0) {
          return UbArray(emptyList())
        }
        type =
          if (valueType == NO_VALUE_TYPE) {
            reader.readByte()
          } else {
            valueType
          }
      }

      val values = ArrayList<UbValue>()
      while (reader.available() > 0 && type != ARRAY_END) {
        values.add(parseValue(type))
        if (expectedSize > UNKNOWN_SIZE && values.size >= expectedSize) {
          break
        }
        type =
          if (valueType == NO_VALUE_TYPE) {
            reader.readByte()
          } else {
            valueType
          }
      }
      return UbArray(values)
    }

    private fun parseObject(): UbObject {
      var type: Byte = reader.readByte()
      var valueType: Byte = NO_VALUE_TYPE
      if (type == VALUE_TYPE_MARKER) {
        valueType = reader.readByte()
        type = reader.readByte()
      }

      var expectedSize = UNKNOWN_SIZE
      if (type == SIZE_MARKER) {
        expectedSize = parseSize(reader.readByte()).toInt()
        if (expectedSize == 0) {
          return UbObject(emptyMap())
        }
        type = reader.readByte()
      }

      val children = LinkedHashMap<String, UbValue>()
      while (reader.available() > 0 && type != OBJECT_END) {
        val key: String = parseStringOptional(type)
        val childType: Byte =
          if (valueType == NO_VALUE_TYPE) {
            reader.readByte()
          } else {
            valueType
          }
        children[key] = parseValue(childType)
        if (expectedSize > UNKNOWN_SIZE && children.size >= expectedSize) {
          break
        }
        type = reader.readByte()
      }
      return UbObject(children)
    }

    private fun parseData(blockType: Byte): UbArray {
      val dataType: Byte = reader.readByte()
      val size: Int =
        if (blockType == LARGE_DATA_TYPE) {
          readUnsignedInt().toInt()
        } else {
          readUnsignedByte()
        }
      val values = ArrayList<UbValue>(size)
      repeat(size) {
        values.add(parseValue(dataType))
      }
      return UbArray(values)
    }

    private fun parseString(type: Byte): String {
      val size: Long =
        when (type) {
          LONG_STRING_TYPE -> parseSize(reader.readByte())
          STRING_TYPE -> readUnsignedByte().toLong()
          else -> error("earth.g3db expected string marker")
        }
      return readString(size)
    }

    private fun parseStringOptional(type: Byte): String {
      if (type == STRING_TYPE || type == LONG_STRING_TYPE) {
        return parseString(type)
      }
      val size: Long = parseSize(type)
      return readString(size)
    }

    private fun parseSize(type: Byte): Long =
      when (type) {
        OLD_SHORT_TYPE -> readUnsignedByte().toLong()
        OLD_INT_TYPE -> reader.readUnsignedShort().toLong()
        INT_TYPE -> readUnsignedInt()
        LONG_TYPE -> reader.readLong()
        else -> error("earth.g3db expected size marker")
      }

    private fun readString(size: Long): String {
      require(size >= 0 && size <= Int.MAX_VALUE) {
        "earth.g3db string size is out of range: $size"
      }
      val bytes = ByteArray(size.toInt())
      reader.readFully(bytes)
      return bytes.toString(Charsets.UTF_8)
    }

    private fun readUnsignedByte(): Int = reader.readUnsignedByte()

    private fun readUnsignedInt(): Long = reader.readInt().toLong() and UINT_MASK
  }

  private const val ATTRIBUTES_FIELD: String = "attributes"
  private const val EXPECTED_MESH_COUNT: Int = 1
  private const val EXPECTED_PART_COUNT: Int = 1
  private const val INDICES_FIELD: String = "indices"
  private const val MAX_UNSIGNED_SHORT_INDEX: Int = 65535
  private const val MESHES_FIELD: String = "meshes"
  private const val MESH_INDEX: Int = 0
  private const val MISSING_OFFSET: Int = -1
  private const val NORMAL_ATTRIBUTE: String = "NORMAL"
  private const val NORMAL_ATTRIBUTE_FLOATS: Int = 3
  private const val PART_INDEX: Int = 0
  private const val PARTS_FIELD: String = "parts"
  private const val POSITION_ATTRIBUTE: String = "POSITION"
  private const val POSITION_ATTRIBUTE_FLOATS: Int = 3
  private const val TEXCOORD0_ATTRIBUTE: String = "TEXCOORD0"
  private const val TEXCOORD0_ATTRIBUTE_FLOATS: Int = 2
  private const val VERTICES_FIELD: String = "vertices"
  private const val UINT_MASK: Long = 0xFFFFFFFFL
  private const val UNKNOWN_SIZE: Int = -1
  private const val NO_VALUE_TYPE: Byte = 0
  private const val ARRAY_START: Byte = '['.code.toByte()
  private const val ARRAY_END: Byte = ']'.code.toByte()
  private const val OBJECT_START: Byte = '{'.code.toByte()
  private const val OBJECT_END: Byte = '}'.code.toByte()
  private const val VALUE_TYPE_MARKER: Byte = '$'.code.toByte()
  private const val SIZE_MARKER: Byte = '#'.code.toByte()
  private const val NULL_TYPE: Byte = 'Z'.code.toByte()
  private const val TRUE_TYPE: Byte = 'T'.code.toByte()
  private const val FALSE_TYPE: Byte = 'F'.code.toByte()
  private const val BYTE_TYPE: Byte = 'B'.code.toByte()
  private const val UNSIGNED_BYTE_TYPE: Byte = 'U'.code.toByte()
  private const val OLD_SHORT_TYPE: Byte = 'i'.code.toByte()
  private const val OLD_INT_TYPE: Byte = 'I'.code.toByte()
  private const val INT_TYPE: Byte = 'l'.code.toByte()
  private const val LONG_TYPE: Byte = 'L'.code.toByte()
  private const val FLOAT_TYPE: Byte = 'd'.code.toByte()
  private const val DOUBLE_TYPE: Byte = 'D'.code.toByte()
  private const val STRING_TYPE: Byte = 's'.code.toByte()
  private const val LONG_STRING_TYPE: Byte = 'S'.code.toByte()
  private const val SMALL_DATA_TYPE: Byte = 'a'.code.toByte()
  private const val LARGE_DATA_TYPE: Byte = 'A'.code.toByte()
}

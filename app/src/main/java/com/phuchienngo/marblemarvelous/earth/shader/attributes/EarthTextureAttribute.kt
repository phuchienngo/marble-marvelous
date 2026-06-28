package com.phuchienngo.marblemarvelous.earth.shader.attributes

import com.badlogic.gdx.graphics.Cubemap
import com.badlogic.gdx.graphics.g3d.Attribute
import com.badlogic.gdx.graphics.g3d.attributes.CubemapAttribute
import com.badlogic.gdx.graphics.g3d.utils.TextureDescriptor
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.utils.GdxRuntimeException

class EarthTextureAttribute private constructor(
    type: Long,
) : Attribute(type) {
    val extraTransform: Matrix4 = Matrix4().idt()
    val textureDescription: TextureDescriptor<Cubemap> = TextureDescriptor()

    init {
        if (!isType(type)) {
            throw GdxRuntimeException("Invalid type specified")
        }
    }

    private constructor(
        type: Long,
        textureDescription: TextureDescriptor<Cubemap>,
        transform: Matrix4,
    ) : this(type) {
        extraTransform.set(transform)
        this.textureDescription.set(textureDescription)
    }

    private constructor(
        type: Long,
        texture: Cubemap,
        transform: Matrix4,
    ) : this(type) {
        extraTransform.set(transform)
        textureDescription.texture = texture
    }

    private constructor(copyFrom: EarthTextureAttribute) : this(
        type = copyFrom.type,
        textureDescription = copyFrom.textureDescription,
        transform = copyFrom.extraTransform,
    )

    override fun copy(): Attribute = EarthTextureAttribute(this)

    override fun hashCode(): Int {
        val result: Int = super.hashCode()
        return (HASH_MULTIPLIER * result) + textureDescription.hashCode()
    }

    override fun compareTo(other: Attribute): Int {
        if (type != other.type) {
            return (type - other.type).toInt()
        }
        val otherTextureAttribute: EarthTextureAttribute = other as EarthTextureAttribute
        return textureDescription.compareTo(otherTextureAttribute.textureDescription)
    }

    companion object {
        val DAY_DIFFUSE: Long = register("DayDiffuse")
        val NIGHT_DIFFUSE: Long = register("NightDiffuse")
        private val MASK: Long = CubemapAttribute.EnvironmentMap or DAY_DIFFUSE or NIGHT_DIFFUSE
        private const val HASH_MULTIPLIER: Int = 967

        fun isType(mask: Long): Boolean = (MASK and mask) != 0L

        fun createDay(texture: Cubemap): EarthTextureAttribute =
            EarthTextureAttribute(
                type = DAY_DIFFUSE,
                texture = texture,
                transform = Matrix4().idt(),
            )

        fun createNight(texture: Cubemap): EarthTextureAttribute =
            EarthTextureAttribute(
                type = NIGHT_DIFFUSE,
                texture = texture,
                transform = Matrix4().idt(),
            )
    }
}

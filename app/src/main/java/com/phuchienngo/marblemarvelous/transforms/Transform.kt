package com.phuchienngo.marblemarvelous.transforms

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3
import kotlin.math.sqrt

class Transform : Matrix4() {
    @JvmField var position = Vector3()

    @JvmField var rotation = Quaternion()

    @JvmField var scale = Vector3(1.0f, 1.0f, 1.0f)
    private val zeroState: Quaternion = Quaternion()
    private val rotationRaw: Quaternion = Quaternion()

    fun setAngles(angles: Vector3) {
        rotationRaw.setEulerAngles(angles.y, angles.x, angles.z)
        rotation.set(rotationRaw)
    }

    fun setAnglesFromCartesiansPosition(newPos: Vector3) {
        val normNewPos: Vector3 = Vector3(newPos)
        normNewPos.nor()
        val cross: Vector3 = Vector3(position)
        cross.nor()
        val dotProduct: Float = position.dot(normNewPos)
        val a: Vector3 = cross.crs(normNewPos)
        val w: Double = sqrt(position.len2() * normNewPos.len2().toDouble()) + dotProduct.toDouble()
        rotationRaw.idt()
        if (dotProduct < 1.0f) {
            if (dotProduct <= -1.0f) {
                rotationRaw.setEulerAngles(180.0f, 0.0f, 0.0f)
            } else {
                rotationRaw.set(a.x, a.y, a.z, w.toFloat())
            }
        }
        rotation.set(rotationRaw)
    }

    fun setAnglesWithRemapping(clamping: Float) {
        val clamp: Float = MathUtils.clamp(clamping, 0.0f, 1.0f)
        rotation.set(rotationRaw)
        rotation.slerp(zeroState, clamp)
    }

    fun lerp(
        transformTarget: Transform,
        alpha: Float
    ) {
        val clmpAlpha: Float = MathUtils.clamp(alpha, 0.0f, 1.0f)
        position.lerp(transformTarget.position, clmpAlpha)
        rotation.slerp(transformTarget.rotation, clmpAlpha)
        scale.lerp(transformTarget.scale, clmpAlpha)
    }

    fun set(transformTarget: Transform) {
        position.set(transformTarget.position)
        rotation.set(transformTarget.rotation)
        scale.set(transformTarget.scale)
    }

    override fun setToScaling(
        scaleX: Float,
        scaleY: Float,
        scaleZ: Float
    ): Matrix4 {
        super.setToScaling(scaleX, scaleY, scaleZ)
        getScale(scale)
        return this
    }

    fun update() {
        set(position, rotation, scale)
    }
}

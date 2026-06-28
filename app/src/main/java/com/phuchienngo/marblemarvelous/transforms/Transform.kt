package com.phuchienngo.marblemarvelous.transforms

import kotlin.math.*

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.Vector3

class Transform : Matrix4() {
    @JvmField var position = Vector3()
    @JvmField var rotation = Quaternion()
    @JvmField var scale = Vector3(1.0f, 1.0f, 1.0f)
    private val zeroState = Quaternion()
    private val rotationRaw = Quaternion()

    fun setAngles(angles: Vector3) {
        rotationRaw.setEulerAngles(angles.y, angles.x, angles.z)
        rotation.set(rotationRaw)
    }

    fun setAnglesFromCartesiansPosition(newPos: Vector3) {
        val normNewPos = Vector3(newPos)
        normNewPos.nor()
        val cross = Vector3(position)
        cross.nor()
        val dotProduct = position.dot(normNewPos)
        val a = cross.crs(normNewPos)
        val w = sqrt(position.len2() * normNewPos.len2().toDouble()) + dotProduct.toDouble()
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
        val clamp = MathUtils.clamp(clamping, 0.0f, 1.0f)
        rotation.set(rotationRaw)
        rotation.slerp(zeroState, clamp)
    }

    fun lerp(transformTarget: Transform, alpha: Float) {
        val clmpAlpha = MathUtils.clamp(alpha, 0.0f, 1.0f)
        position.lerp(transformTarget.position, clmpAlpha)
        rotation.slerp(transformTarget.rotation, clmpAlpha)
        scale.lerp(transformTarget.scale, clmpAlpha)
    }

    fun set(transformTarget: Transform) {
        position.set(transformTarget.position)
        rotation.set(transformTarget.rotation)
        scale.set(transformTarget.scale)
    }

    override fun setToScaling(scaleX: Float, scaleY: Float, scaleZ: Float): Matrix4 {
        super.setToScaling(scaleX, scaleY, scaleZ)
        getScale(scale)
        return this
    }

    fun update() {
        set(position, rotation, scale)
    }
}

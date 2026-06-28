package com.phuchienngo.marblemarvelous.transforms

import kotlin.math.*

import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3

class SphericalTransform {
    private var center = Vector3()
    private val vectorTmp = Vector3()
    private var theta = 0.0f
    private var phi = 0.0f
    private var r = 1.0f
    private val cartesian = Vector3()

    constructor(thetaRad: Float, phiRad: Float, radius: Float) :
        this(thetaRad, phiRad, radius, Vector3.Zero)

    constructor(thetaRad: Float, phiRad: Float, radius: Float, center: Vector3) {
        set(thetaRad, phiRad, radius, center)
    }

    constructor(transform: SphericalTransform) {
        set(transform)
    }

    constructor(position: Vector3) {
        updateSphericalFromCartesian(position, center)
        updateCartesians()
    }

    constructor(position: Vector3, center: Vector3) {
        this.center = center
        updateSphericalFromCartesian(position, center)
        updateCartesians()
    }

    fun lerp(to: SphericalTransform, progress: Float) = lerp(this, to, progress)

    fun lerp(from: SphericalTransform, to: SphericalTransform, progress: Float) {
        setRadius(MathUtils.lerp(from.r, to.r, progress))
        setPhi(MathUtils.lerpAngle(from.phi, to.phi, progress))
        setTheta(MathUtils.lerpAngle(from.theta, to.theta, progress))
        vectorTmp.set(from.center)
        vectorTmp.lerp(to.center, progress)
        setCenter(vectorTmp)
        updateCartesians()
    }

    fun getCartesianCoordinates(): Vector3 = cartesian

    fun setCenter(center: Vector3) {
        this.center.set(center)
    }

    fun update() = updateCartesians()

    fun set(transform: SphericalTransform) =
        set(transform.theta, transform.phi, transform.r, transform.getCenter())

    fun set(theta: Float, phi: Float, radius: Float) = set(theta, phi, radius, center)

    fun set(theta: Float, phi: Float, radius: Float, center: Vector3) {
        setRadius(radius)
        setPhi(phi)
        setTheta(theta)
        setCenter(center)
        updateCartesians()
    }

    fun setRadius(radius: Float) {
        r = radius
    }

    fun setPhi(phiRad: Float) {
        phi = (phiRad + 6.2831855f) % 6.2831855f
    }

    fun setTheta(thetaRad: Float) {
        theta = thetaRad
    }

    fun getRadius(): Float = r
    fun getThetaRad(): Float = theta
    fun getThetaDeg(): Float = theta * 57.29578f
    fun getPhiRad(): Float = phi
    fun getCenter(): Vector3 = center
    fun getPhiDeg(): Float = phi * 57.29578f

    fun setFromCartesiansPosition(position: Vector3) = setFromCartesiansPosition(position, center)

    fun setFromCartesiansPosition(position: Vector3, center: Vector3) {
        this.center = center
        updateSphericalFromCartesian(position, center)
    }

    override fun toString(): String =
        "(" + getThetaDeg() + ", " + getPhiDeg() + ", " + getRadius() + "), center: " + getCenter()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val transform = other as SphericalTransform
        return transform.theta == theta && transform.phi == phi && transform.r == r &&
            center == transform.center
    }

    override fun hashCode(): Int {
        var result = theta.hashCode()
        result = 31 * result + phi.hashCode()
        result = 31 * result + r.hashCode()
        result = 31 * result + center.hashCode()
        return result
    }

    private fun updateCartesians() {
        cartesian.y = (r * cos(theta.toDouble())).toFloat() + center.y
        cartesian.z = (r * sin(theta.toDouble()) * cos(phi.toDouble())).toFloat() + center.z
        cartesian.x = (r * sin(theta.toDouble()) * sin(phi.toDouble())).toFloat() + center.x
    }

    private fun updateSphericalFromCartesian(position: Vector3, center: Vector3) {
        vectorTmp.set(position)
        vectorTmp.sub(center)
        r = vectorTmp.len()
        theta = acos((vectorTmp.y / r).toDouble()).toFloat()
        phi = atan2(vectorTmp.x.toDouble(), vectorTmp.z.toDouble()).toFloat()
    }

    companion object {
        const val DEG_TO_RAD = 0.017453292f
        const val RADIANS_TO_DEG = 57.29578f
    }
}

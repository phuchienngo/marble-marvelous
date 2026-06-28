package com.phuchienngo.marblemarvelous.animations

import com.badlogic.gdx.math.MathUtils
import com.phuchienngo.marblemarvelous.utils.Easing as EasingFn

class TweenController {
    @JvmField val DEFAULT_EASING: Easing = Easing.EXPO_OUT
    protected var easing = DEFAULT_EASING
    private var duration = 1.0f
    private var delay = 0.0f
    private var durationDelay = 0.0f
    private var transition = 0.0f
    private var initValue = 0.0f
    private var endValue = 1.0f
    private var transitionConverted = initValue
    private var elapsedTime = 0.0f
    private var animating = false

    enum class Easing {
        EXPO_IN_OUT,
        EXPO_OUT,
        EXPO_IN,
        LINEAR,
        QUART_OUT,
        QUAD_OUT,
        BACK_IN,
        BACK_OUT,
        BACK_IN_OUT,
    }

    fun setTween(
        fromValue: Float,
        toValue: Float,
        duration: Float
    ) = setTween(fromValue, toValue, duration, 0.0f)

    fun setTween(
        fromValue: Float,
        toValue: Float,
        duration: Float,
        delay: Float
    ) = setTween(fromValue, toValue, duration, delay, easing)

    fun setTween(
        fromValue: Float,
        toValue: Float,
        duration: Float,
        easing: Easing
    ) = setTween(fromValue, toValue, duration, 0.0f, easing)

    fun setTween(
        fromValue: Float,
        toValue: Float,
        duration: Float,
        delay: Float,
        easing: Easing
    ) {
        animating = false
        setInitValue(fromValue)
        setEndValue(toValue)
        setDuration(duration)
        setDelay(delay)
        this.easing = easing
        reset()
    }

    fun to(
        toValue: Float,
        duration: Float,
        delay: Float,
        easing: Easing
    ) {
        setTween(transitionConverted, toValue, duration, delay, easing)
        start()
    }

    fun to(
        toValue: Float,
        duration: Float,
        easing: Easing
    ) = to(toValue, duration, 0.0f, easing)

    fun to(
        toValue: Float,
        duration: Float
    ) = to(toValue, duration, 0.0f, easing)

    fun setDuration(duration: Float) {
        this.duration = duration
    }

    fun setDelay(delay: Float) {
        this.delay = delay
    }

    fun setInitValue(initValue: Float) {
        this.initValue = initValue
    }

    fun setEndValue(endValue: Float) {
        this.endValue = endValue
    }

    fun start() {
        reset()
        animating = true
    }

    fun reset() {
        elapsedTime = -delay
        transition = 0.0f
        transitionConverted = initValue
        animating = false
    }

    fun stop() {
        elapsedTime = duration + durationDelay
        transition = 1.0f
        transitionConverted = endValue
        animating = false
    }

    fun set(toValue: Float) {
        animating = false
        elapsedTime = 0.0f
        transitionConverted = toValue
        transition = 1.0f
        setInitValue(toValue)
        setEndValue(toValue)
        setDuration(0.0f)
        setDelay(0.0f)
    }

    fun pause() {
        animating = false
    }

    fun resume() {
        animating = true
    }

    fun update(delta: Float): Boolean {
        if (!animating) {
            return false
        }
        updateValues(delta)
        animating = transition < 1.0f || elapsedTime < duration + durationDelay
        return transition < 1.0f
    }

    fun getValue(): Float = transitionConverted

    fun setDurationDelay(durationDelay: Float) {
        this.durationDelay = durationDelay
    }

    fun getValueDelayed(delay: Float): Float {
        val transitionTmp: Float = easeTransition(MathUtils.clamp(elapsedTime - delay, 0.0f, duration), duration)
        return ((endValue - initValue) * transitionTmp) + initValue
    }

    fun isAnimating(): Boolean = animating

    fun currentPosition(): Float = MathUtils.clamp(elapsedTime, 0.0f, duration) / duration

    fun goTo(position: Float) {
        elapsedTime = duration * MathUtils.clamp(position, 0.0f, 1.0f)
        updateValues(0.0f)
    }

    private fun updateValues(delta: Float) {
        elapsedTime += delta
        transition = easeTransition(MathUtils.clamp(elapsedTime, 0.0f, duration), duration)
        transitionConverted = ((endValue - initValue) * transition) + initValue
    }

    private fun easeTransition(
        elapsedTime: Float,
        duration: Float
    ): Float =
        when (easing) {
            Easing.LINEAR -> EasingFn.linear(elapsedTime, 0.0f, 1.0f, duration)
            Easing.QUAD_OUT -> EasingFn.quadEaseOut(elapsedTime, 0.0f, 1.0f, duration)
            Easing.QUART_OUT -> EasingFn.quartEaseOut(elapsedTime, 0.0f, 1.0f, duration)
            Easing.BACK_IN -> EasingFn.backEaseIn(elapsedTime, 0.0f, 1.0f, duration)
            Easing.BACK_OUT -> EasingFn.backEaseOut(elapsedTime, 0.0f, 1.0f, duration)
            Easing.BACK_IN_OUT -> EasingFn.backEaseInOut(elapsedTime, 0.0f, 1.0f, duration)
            Easing.EXPO_IN_OUT -> EasingFn.expoEaseInOut(elapsedTime, 0.0f, 1.0f, duration)
            Easing.EXPO_IN -> EasingFn.expoEaseIn(elapsedTime, 0.0f, 1.0f, duration)
            Easing.EXPO_OUT -> EasingFn.expoEaseOut(elapsedTime, 0.0f, 1.0f, duration)
        }
}

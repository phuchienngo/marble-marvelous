package com.phuchienngo.marblemarvelous.view.controller

import android.view.MotionEvent
import com.phuchienngo.marblemarvelous.di.WallpaperServiceScope
import com.phuchienngo.marblemarvelous.view.interfaces.TouchListener
import javax.inject.Inject
import kotlin.math.min

@WallpaperServiceScope
class TouchController @Inject constructor(private val listener: TouchListener) {

    enum class TouchType(val value: String) {
        DOWN("DOWN"),
        UP("UP"),
        MOVE("MOVE")
    }

    private val mLastTouchPositionX: FloatArray = floatArrayOf(-1.0f, -1.0f, -1.0f, -1.0f)
    private val mLastTouchPositionY: FloatArray = floatArrayOf(-1.0f, -1.0f, -1.0f, -1.0f)

    fun processTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            0, 5 -> processTouchDown(event)
            1, 6 -> processTouchUp(event)
            2 -> processTouchMovement(event)
            else -> return
        }
    }

    private fun processTouchDown(event: MotionEvent) {
        val idx: Int = event.pointerCount - 1
        if (idx < 0 || idx >= MAX_POINTER) {
            return
        }
        mLastTouchPositionX[idx] = event.getX(idx)
        mLastTouchPositionY[idx] = event.getY(idx)
        listener.onTouchProcessed(
            mLastTouchPositionX[idx].toInt(),
            mLastTouchPositionY[idx].toInt(),
            idx,
            TouchType.DOWN
        )
    }

    private fun processTouchUp(event: MotionEvent) {
        val idx: Int = event.pointerCount - 1
        if (idx < 0 || idx >= MAX_POINTER) {
            return
        }
        mLastTouchPositionX[idx] = event.getX(idx)
        mLastTouchPositionY[idx] = event.getY(idx)
        listener.onTouchProcessed(
            mLastTouchPositionX[idx].toInt(),
            mLastTouchPositionY[idx].toInt(),
            idx,
            TouchType.UP
        )
    }

    private fun processTouchMovement(event: MotionEvent) {
        val count: Int = min(event.pointerCount, MAX_POINTER)
        for (i in 0 until count) {
            if (mLastTouchPositionX[i] != event.getX(i) || mLastTouchPositionY[i] != event.getY(i)) {
                mLastTouchPositionX[i] = event.getX(i)
                mLastTouchPositionY[i] = event.getY(i)
                listener.onTouchProcessed(
                    mLastTouchPositionX[i].toInt(),
                    mLastTouchPositionY[i].toInt(),
                    i,
                    TouchType.MOVE
                )
            }
        }
    }

    companion object {
        private const val MAX_POINTER: Int = 4
    }
}

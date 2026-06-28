package com.phuchienngo.marblemarvelous.input

interface InputProcessor {
    fun touchDown(
        i: Int,
        i2: Int,
        i3: Int,
    ): Boolean

    fun touchDragged(
        i: Int,
        i2: Int,
        i3: Int,
    ): Boolean

    fun touchUp(
        i: Int,
        i2: Int,
        i3: Int,
    ): Boolean
}

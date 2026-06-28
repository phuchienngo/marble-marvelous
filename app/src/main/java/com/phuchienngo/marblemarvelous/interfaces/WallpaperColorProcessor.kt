package com.phuchienngo.marblemarvelous.interfaces

import android.graphics.Color

interface WallpaperColorProcessor {
    fun mainWallpaperColor(): Color
    fun secondaryWallpaperColor(): Color?
    fun tertiaryWallpaperColor(): Color?
}

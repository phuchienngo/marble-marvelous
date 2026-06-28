package com.phuchienngo.marblemarvelous.wallpaper.controller

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.phuchienngo.marblemarvelous.di.WallpaperServiceScope
import com.phuchienngo.marblemarvelous.wallpaper.listener.PowerSaveListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@WallpaperServiceScope
class PowerSaveController
    @Inject
    constructor(
        context: Context,
        private val listener: PowerSaveListener
    ) : ViewController(context) {
        private var powerManager: PowerManager? = null
        private var savingPower: AtomicBoolean? = null

        override fun init(): Boolean {
            if (savingPower == null) {
                val defaultSavingPower: Boolean = false
                savingPower = AtomicBoolean(defaultSavingPower)
            }
            if (powerManager == null) {
                powerManager = context.getSystemService("power") as PowerManager?
            }
            return powerManager != null
        }

        override fun onBroadcastReceived(
            context: Context,
            intent: Intent,
            action: String
        ) {
            if (action == "android.os.action.POWER_SAVE_MODE_CHANGED") {
                setSavingPower(isPowerSave = powerManager!!.isPowerSaveMode, fire = true)
            }
        }

        override fun onRegister(fireStraightAway: Boolean): IntentFilter {
            setSavingPower(isPowerSave = powerManager!!.isPowerSaveMode, fire = fireStraightAway)
            return IntentFilter("android.os.action.POWER_SAVE_MODE_CHANGED")
        }

        override fun onUnregister() {}

        fun isPowerSaveMode(): Boolean = savingPower!!.get()

        private fun setSavingPower(
            isPowerSave: Boolean,
            fire: Boolean
        ) {
            if (savingPower!!.get() == isPowerSave) {
                return
            }
            savingPower!!.set(isPowerSave)
            if (fire) {
                listener.onPowerSaveModeChanged(isPowerSave)
            }
        }

        companion object {
            const val DEFAULT_POWER_SAVE_MODE: Boolean = false
        }
    }

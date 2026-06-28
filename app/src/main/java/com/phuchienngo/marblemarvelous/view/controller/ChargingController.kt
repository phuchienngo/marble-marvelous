package com.phuchienngo.marblemarvelous.view.controller

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.phuchienngo.marblemarvelous.di.WallpaperServiceScope
import com.phuchienngo.marblemarvelous.view.interfaces.ChargingListener
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@WallpaperServiceScope
class ChargingController @Inject constructor(context: Context, private val listener: ChargingListener) :
    ViewController(context) {

    private var chargingState: AtomicBoolean? = null

    override fun init(): Boolean {
        if (chargingState == null) {
            val defaultChargingState: Boolean = false
            chargingState = AtomicBoolean(defaultChargingState)
        }
        return true
    }

    override fun onBroadcastReceived(context: Context, intent: Intent, action: String) {
        if (action == "android.intent.action.ACTION_POWER_DISCONNECTED" ||
            action == "android.intent.action.ACTION_POWER_CONNECTED"
        ) {
            updateChargingState(
                charging = action == "android.intent.action.ACTION_POWER_CONNECTED",
                fire = true
            )
        }
    }

    override fun onRegister(fireStraightAway: Boolean): IntentFilter {
        updateChargingState(fire = fireStraightAway)
        val intentFilter: IntentFilter = IntentFilter("android.intent.action.ACTION_POWER_CONNECTED")
        intentFilter.addAction("android.intent.action.ACTION_POWER_DISCONNECTED")
        return intentFilter
    }

    override fun onUnregister() {}

    fun isCharging(): Boolean = chargingState!!.get()

    private fun updateChargingState(fire: Boolean) {
        val ifilter: IntentFilter = IntentFilter("android.intent.action.BATTERY_CHANGED")
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)
        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isChargingTmp: Boolean = status == 2 || status == 5
        chargingState!!.set(isChargingTmp)
        if (fire) {
            listener.onChargingStateChanged(isChargingTmp)
        }
    }

    private fun updateChargingState(charging: Boolean, fire: Boolean) {
        chargingState!!.set(charging)
        if (fire) {
            listener.onChargingStateChanged(charging)
        }
    }

    companion object {
        const val DEFAULT_CHARGING_MODE: Boolean = false
    }
}

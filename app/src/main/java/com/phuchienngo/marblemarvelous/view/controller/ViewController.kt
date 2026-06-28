package com.phuchienngo.marblemarvelous.view.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.util.concurrent.atomic.AtomicBoolean

abstract class ViewController internal constructor(@JvmField protected val context: Context) {

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action ?: return
            onBroadcastReceived(context, intent, action)
        }
    }
    private val broadcastRegistered: AtomicBoolean = AtomicBoolean(DEFAULT_NOT_SET)
    // NOTE: original called init() from the constructor. Deferred to the first
    // resume() to avoid Kotlin's open-call-in-constructor init-order pitfall.
    private val initialized: AtomicBoolean = AtomicBoolean(DEFAULT_NOT_SET)

    protected abstract fun init(): Boolean
    protected abstract fun onBroadcastReceived(context: Context, intent: Intent, action: String)
    protected abstract fun onRegister(fireStraightAway: Boolean): IntentFilter
    protected abstract fun onUnregister()

    fun resume(fireStraightAway: Boolean) {
        if (!initialized.get()) {
            if (!init()) {
                return
            }
            val initializedValue: Boolean = true
            initialized.set(initializedValue)
        }
        if (!broadcastRegistered.get()) {
            val filter: IntentFilter = onRegister(fireStraightAway)
            registerReceiver(context, broadcastReceiver, filter)
            val broadcastRegisteredValue: Boolean = true
            broadcastRegistered.set(broadcastRegisteredValue)
        }
    }

    @Synchronized
    fun pause() {
        if (broadcastRegistered.get()) {
            onUnregister()
            unregisterReceiver(context, broadcastReceiver)
            broadcastRegistered.set(DEFAULT_NOT_SET)
        }
    }

    @Synchronized
    fun dispose() {
        pause()
    }

    protected open fun registerReceiver(context: Context, receiver: BroadcastReceiver, filter: IntentFilter) {
        context.registerReceiver(receiver, filter)
    }

    protected open fun unregisterReceiver(context: Context, receiver: BroadcastReceiver) {
        context.unregisterReceiver(receiver)
    }

    companion object {
        private const val DEFAULT_NOT_SET: Boolean = false
    }
}

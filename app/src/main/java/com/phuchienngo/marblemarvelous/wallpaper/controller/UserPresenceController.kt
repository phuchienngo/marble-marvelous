package com.phuchienngo.marblemarvelous.wallpaper.controller

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.phuchienngo.marblemarvelous.di.WallpaperServiceScope
import com.phuchienngo.marblemarvelous.utils.Console
import com.phuchienngo.marblemarvelous.wallpaper.listener.UserPresenceListener
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

@WallpaperServiceScope
class UserPresenceController
    @Inject
    constructor(
        context: Context,
        private val listener: UserPresenceListener,
    ) : ViewController(context) {
        private var ambientMode: AtomicBoolean? = null
        private var animate: AtomicBoolean? = null
        private var rawUserPresence: AtomicReference<String>? = null
        private var userPresence: AtomicReference<String>? = null

        override fun init(): Boolean {
            if (userPresence == null) {
                userPresence = AtomicReference("unlocked")
            }
            if (rawUserPresence == null) {
                rawUserPresence = AtomicReference("unlocked")
            }
            if (ambientMode == null) {
                val defaultAmbientMode: Boolean = false
                ambientMode = AtomicBoolean(defaultAmbientMode)
            }
            if (animate == null) {
                val defaultAnimate: Boolean = true
                animate = AtomicBoolean(defaultAnimate)
            }
            return true
        }

        override fun onBroadcastReceived(
            context: Context,
            intent: Intent,
            action: String,
        ) {
            var newPresence: String = getPresenceFromAction(context, action)
            var newAnimate: Boolean = true
            Console.log(TAG, "/// NEW PRESENCE MODE: $newPresence, AMBIENT: $ambientMode action:$action")
            if (newPresence != "") {
                setRawUserPresence(newPresence)
                if (ambientMode!!.get()) {
                    newPresence = PRESENCE_AOD
                    newAnimate = animate!!.get()
                }
                setUserPresence(userPresence = newPresence, animate = newAnimate, fire = true)
            }
        }

        private fun getPresenceFromAction(
            context: Context,
            action: String,
        ): String {
            if (action == "android.intent.action.SCREEN_OFF") {
                return PRESENCE_OFF
            }
            if (action == "android.intent.action.USER_PRESENT") {
                return PRESENCE_UNLOCKED
            }
            if (action == "android.intent.action.SCREEN_ON") {
                val keyguardManager: KeyguardManager? = context.getSystemService("keyguard") as KeyguardManager?
                if (keyguardManager != null) {
                    return if (keyguardManager.isKeyguardLocked) {
                        PRESENCE_LOCKED
                    } else {
                        PRESENCE_UNLOCKED
                    }
                }
            }
            return ""
        }

        override fun onRegister(fireStraightAway: Boolean): IntentFilter {
            val keyguardManager: KeyguardManager? = context.getSystemService("keyguard") as KeyguardManager?
            if (keyguardManager != null) {
                Console.log(TAG, "/// Init: " + keyguardManager.isKeyguardLocked)
                val presence: String =
                    if (keyguardManager.isKeyguardLocked) {
                        PRESENCE_LOCKED
                    } else {
                        PRESENCE_UNLOCKED
                    }
                setRawUserPresence(presence)
                setUserPresence(userPresence = presence, animate = false, fire = fireStraightAway)
            }
            val intentFilter: IntentFilter = IntentFilter("android.intent.action.USER_PRESENT")
            intentFilter.addAction("android.intent.action.SCREEN_OFF")
            intentFilter.addAction("android.intent.action.SCREEN_ON")
            return intentFilter
        }

        override fun onUnregister() {}

        fun updateAmbientMode(
            ambientMode: Boolean,
            animate: Boolean,
        ) {
            if (ambientMode == this.ambientMode!!.get() && animate == this.animate!!.get()) {
                return
            }
            this.ambientMode!!.set(ambientMode)
            this.animate!!.set(animate)
            Console.log(TAG, "/// AMBIENT MODE: $ambientMode $animate")
            if (rawUserPresence!!.get() == PRESENCE_OFF) {
                return
            }
            val presence: String =
                if (ambientMode) {
                    PRESENCE_AOD
                } else {
                    rawUserPresence!!.get()
                }
            setUserPresence(userPresence = presence, animate = animate, fire = true)
        }

        private fun setRawUserPresence(userPresence: String) {
            if (rawUserPresence!!.get() == userPresence) {
                return
            }
            rawUserPresence!!.set(userPresence)
        }

        private fun setUserPresence(
            userPresence: String,
            animate: Boolean,
            fire: Boolean,
        ) {
            if (this.userPresence!!.get() == userPresence) {
                return
            }
            this.userPresence!!.set(userPresence)
            if (fire) {
                listener.onUserPresenceChanged(userPresence, animate)
            }
        }

        companion object {
            const val DEFAULT_AMBIENT_MODE: Boolean = false
            const val DEFAULT_ANIMATE_TRANSITION: Boolean = true
            const val DEFAULT_PRESENCE: String = "unlocked"
            const val PRESENCE_AOD: String = "aod"
            const val PRESENCE_LOCKED: String = "locked"
            const val PRESENCE_OFF: String = "off"
            const val PRESENCE_UNLOCKED: String = "unlocked"
            private val TAG: String = UserPresenceController::class.java.toString()
        }
    }

package com.phuchienngo.marblemarvelous.permissions

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicBoolean

abstract class UserPermissions(
    private val context: Context,
    initialListener: PermissionsListener,
) : SharedPreferences.OnSharedPreferenceChangeListener {
    private var listener: PermissionsListener? = null
    private val permissionsGranted = AtomicBoolean(false)
    private val permissionsAsked = AtomicBoolean(false)
    private val registered = AtomicBoolean(false)
    private val permissions: Array<String> = getPermissions()

    protected abstract fun getPermissions(): Array<String>

    protected abstract fun getSharedPrefKey(): String

    init {
        val granted = checkPermissionsGranted()
        val asked = checkPermissionsAsked()
        if (granted) {
            initialListener.onPermissionsAccepted(getSharedPrefKey())
        } else if (!asked) {
            listener = initialListener
        }
        permissionsGranted.set(granted)
        permissionsAsked.set(asked)
    }

    private fun checkPermissionsAsked(): Boolean {
        val secureContext = context.createDeviceProtectedStorageContext()
        val preferences = secureContext.getSharedPreferences(APP_PERMISSIONS, 0)
        return preferences.getBoolean(getSharedPrefKey() + ASKED_PREFIX, false)
    }

    fun arePermissionsGranted(): Boolean {
        val granted: Boolean = checkPermissionsGranted()
        permissionsGranted.set(granted)
        return granted
    }

    fun werePermissionsAsked(): Boolean = permissionsAsked.get()

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?,
    ) {
        if (key != null && key == getSharedPrefKey()) {
            permissionsGranted.set(sharedPreferences.getBoolean(key, false))
            permissionsAsked.set(sharedPreferences.getBoolean(key + ASKED_PREFIX, true))
            if (permissionsGranted.get()) {
                listener?.onPermissionsAccepted(key)
            }
            unregister()
        }
    }

    fun dispose() {
        listener = null
        unregister()
    }

    fun requestPermissions() {
        if (!permissionsGranted.get() && !permissionsAsked.get()) {
            val secureContext = context.createDeviceProtectedStorageContext()
            val preferences = secureContext.getSharedPreferences(APP_PERMISSIONS, 0)
            registered.set(true)
            preferences.registerOnSharedPreferenceChangeListener(this)
            val intent = Intent(context, PermissionsActivity::class.java)
            intent.flags = 276824064
            intent.putExtra(PERMISSIONS_REQUESTED, permissions)
            intent.putExtra(SHARED_PREF_KEY, getSharedPrefKey())
            context.startActivity(intent)
        }
    }

    private fun unregister() {
        if (registered.get()) {
            listener = null
            val secureContext = context.createDeviceProtectedStorageContext()
            val preferences = secureContext.getSharedPreferences(APP_PERMISSIONS, 0)
            preferences.unregisterOnSharedPreferenceChangeListener(this)
            registered.set(false)
        }
    }

    private fun checkPermissionsGranted(): Boolean {
        var pg = true
        for (permission in permissions) {
            pg = context.checkSelfPermission(permission) == 0
            if (!pg) break
        }
        return pg
    }

    companion object {
        private const val APP_PERMISSIONS = "PERMISSIONS"
        private const val ASKED_PREFIX = "_ASKED"
        const val PERMISSIONS_REQUESTED = "PERMISSIONS_REQUESTED"
        const val SHARED_PREF_KEY = "SHARED_PREF_KEY"
    }
}

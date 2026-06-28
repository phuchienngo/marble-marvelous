package com.phuchienngo.marblemarvelous.permissions

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import java.util.concurrent.atomic.AtomicBoolean

abstract class UserPermissions(
    private val context: Context,
    initialListener: PermissionsListener
) : SharedPreferences.OnSharedPreferenceChangeListener {
    private var listener: PermissionsListener? = null
    private val permissionsGranted: AtomicBoolean = AtomicBoolean(PERMISSIONS_NOT_GRANTED)
    private val permissionsAsked: AtomicBoolean = AtomicBoolean(PERMISSIONS_NOT_ASKED)
    private val registered: AtomicBoolean = AtomicBoolean(LISTENER_NOT_REGISTERED)
    private val permissions: Array<String> = getPermissions()

    protected abstract fun getPermissions(): Array<String>

    protected abstract fun getSharedPrefKey(): String

    init {
        val granted: Boolean = checkPermissionsGranted()
        val asked: Boolean = checkPermissionsAsked()
        if (granted) {
            initialListener.onPermissionsAccepted(getSharedPrefKey())
        } else {
            if (!asked) {
                listener = initialListener
            }
        }
        permissionsGranted.set(granted)
        permissionsAsked.set(asked)
    }

    private fun checkPermissionsAsked(): Boolean {
        val secureContext: Context = context.createDeviceProtectedStorageContext()
        val preferences: SharedPreferences = secureContext.getSharedPreferences(APP_PERMISSIONS, 0)
        return preferences.getBoolean(getSharedPrefKey() + ASKED_PREFIX, PERMISSIONS_NOT_ASKED)
    }

    fun arePermissionsGranted(): Boolean {
        val granted: Boolean = checkPermissionsGranted()
        permissionsGranted.set(granted)
        return granted
    }

    fun werePermissionsAsked(): Boolean = permissionsAsked.get()

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String?
    ) {
        if (key != null && key == getSharedPrefKey()) {
            permissionsGranted.set(sharedPreferences.getBoolean(key, PERMISSIONS_NOT_GRANTED))
            permissionsAsked.set(sharedPreferences.getBoolean(key + ASKED_PREFIX, PERMISSIONS_ASKED))
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
            val secureContext: Context = context.createDeviceProtectedStorageContext()
            val preferences: SharedPreferences = secureContext.getSharedPreferences(APP_PERMISSIONS, 0)
            registered.set(LISTENER_REGISTERED)
            preferences.registerOnSharedPreferenceChangeListener(this)
            val intent: Intent = Intent(context, PermissionsActivity::class.java)
            intent.flags = 276824064
            intent.putExtra(PERMISSIONS_REQUESTED, permissions)
            intent.putExtra(SHARED_PREF_KEY, getSharedPrefKey())
            context.startActivity(intent)
        }
    }

    private fun unregister() {
        if (registered.get()) {
            listener = null
            val secureContext: Context = context.createDeviceProtectedStorageContext()
            val preferences: SharedPreferences = secureContext.getSharedPreferences(APP_PERMISSIONS, 0)
            preferences.unregisterOnSharedPreferenceChangeListener(this)
            registered.set(LISTENER_NOT_REGISTERED)
        }
    }

    private fun checkPermissionsGranted(): Boolean {
        var pg = true
        for (permission in permissions) {
            pg = context.checkSelfPermission(permission) == 0
            if (!pg) {
                break
            }
        }
        return pg
    }

    companion object {
        private const val APP_PERMISSIONS: String = "PERMISSIONS"
        private const val ASKED_PREFIX: String = "_ASKED"
        private const val LISTENER_NOT_REGISTERED: Boolean = false
        private const val LISTENER_REGISTERED: Boolean = true
        private const val PERMISSIONS_ASKED: Boolean = true
        private const val PERMISSIONS_NOT_ASKED: Boolean = false
        private const val PERMISSIONS_NOT_GRANTED: Boolean = false
        const val PERMISSIONS_REQUESTED: String = "PERMISSIONS_REQUESTED"
        const val SHARED_PREF_KEY: String = "SHARED_PREF_KEY"
    }
}

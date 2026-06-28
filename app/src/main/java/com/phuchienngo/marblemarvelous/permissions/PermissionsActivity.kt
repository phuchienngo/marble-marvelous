package com.phuchienngo.marblemarvelous.permissions

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class PermissionsActivity : Activity() {
    private var permissions: Array<String>? = null
    private var sharedPreferencesKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        permissions = intent.getStringArrayExtra(UserPermissions.PERMISSIONS_REQUESTED)
        sharedPreferencesKey = intent.getStringExtra(UserPermissions.SHARED_PREF_KEY)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
    }

    override fun onResume() {
        super.onResume()
        permissions?.let { requestPermissions(it, PERMISSIONS_REQUEST) }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST) {
            var granted = true
            for (grantResult in grantResults) {
                granted = grantResult == 0
                if (!granted) break
            }
            val secureContext = createDeviceProtectedStorageContext()
            val preferences = secureContext.getSharedPreferences("PERMISSIONS", 0)
            preferences.edit()
                .putBoolean(sharedPreferencesKey, granted)
                .putBoolean(sharedPreferencesKey + "_ASKED", true)
                .apply()
        }
        finish()
    }

    companion object {
        private const val PERMISSIONS_REQUEST = 1
    }
}

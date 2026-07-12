package com.phuchienngo.marblemarvelous.permissions

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import com.phuchienngo.marblemarvelous.R

class PermissionsActivity : Activity() {
  private var permissions: Array<String> = emptyArray()
  private var sharedPreferencesKey: String? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val requestedPermissions: Array<String>? =
      intent.getStringArrayExtra(UserPermissions.PERMISSIONS_REQUESTED)
    permissions =
      if (requestedPermissions != null && requestedPermissions.contentEquals(defaultPermissions())) {
        requestedPermissions
      } else {
        defaultPermissions()
      }
    val requestedKey: String? = intent.getStringExtra(UserPermissions.SHARED_PREF_KEY)
    sharedPreferencesKey =
      requestedKey.takeIf { key: String? -> key == LocationPermissions.LOCATION_KEY }
        ?: LocationPermissions.LOCATION_KEY
    if (permissions.isEmpty()) {
      savePermissionResult(granted = true)
      finish()
      return
    }

    setContentView(permissionRequestView())
    requestPermissions(permissions, PERMISSION_REQUEST_CODE)
  }

  private fun permissionRequestView(): FrameLayout {
    val padding: Int =
      TypedValue
        .applyDimension(TypedValue.COMPLEX_UNIT_DIP, PADDING_DP, resources.displayMetrics)
        .toInt()
    val message =
      TextView(this).apply {
        text = getString(R.string.permissions_request_message)
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, MESSAGE_TEXT_SP)
      }
    return FrameLayout(this).apply {
      setBackgroundColor(SCRIM_COLOR)
      setPadding(padding, padding, padding, padding)
      addView(
        message,
        FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.WRAP_CONTENT,
          FrameLayout.LayoutParams.WRAP_CONTENT,
          Gravity.CENTER
        )
      )
    }
  }

  private fun defaultPermissions(): Array<String> = arrayOf(LocationPermissions.LOCATION_PERMISSION)

  override fun onRequestPermissionsResult(
    requestCode: Int,
    requestedPermissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, requestedPermissions, grantResults)
    if (requestCode != PERMISSION_REQUEST_CODE) {
      return
    }
    val granted: Boolean =
      grantResults.size == permissions.size &&
        grantResults.all { result: Int ->
          return@all result == PackageManager.PERMISSION_GRANTED
      }
    savePermissionResult(granted)
    finish()
  }

  private fun savePermissionResult(granted: Boolean) {
    val preferenceKey: String = sharedPreferencesKey ?: return
    val secureContext: Context = createDeviceProtectedStorageContext()
    val preferences: SharedPreferences =
      secureContext.getSharedPreferences(APP_PERMISSIONS, Context.MODE_PRIVATE)
    preferences
      .edit()
      .putBoolean(preferenceKey, granted)
      .putBoolean(preferenceKey + ASKED_PREFIX, PERMISSION_ASKED)
      .apply()
  }

  private companion object {
    private const val APP_PERMISSIONS: String = "PERMISSIONS"
    private const val ASKED_PREFIX: String = "_ASKED"
    private const val PERMISSION_ASKED: Boolean = true
    private const val PADDING_DP: Float = 24.0f
    private const val MESSAGE_TEXT_SP: Float = 16.0f
    private const val PERMISSION_REQUEST_CODE: Int = 1
    private const val SCRIM_COLOR: Int = 0xDD000000.toInt()
  }
}

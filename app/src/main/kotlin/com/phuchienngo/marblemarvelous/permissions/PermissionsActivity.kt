package com.phuchienngo.marblemarvelous.permissions

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.phuchienngo.marblemarvelous.R
import androidx.core.content.edit

class PermissionsActivity : ComponentActivity() {
  private var permissions: Array<String> = emptyArray()
  private var sharedPreferencesKey: String? = null
  private val requestPermissions: ActivityResultLauncher<Array<String>> =
    registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      permissionResults: Map<String, Boolean> ->
      onPermissionResult(permissionResults)
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    permissions =
      intent.getStringArrayExtra(UserPermissions.PERMISSIONS_REQUESTED) ?: defaultPermissions()
    sharedPreferencesKey =
      intent.getStringExtra(UserPermissions.SHARED_PREF_KEY) ?: LocationPermissions.LOCATION_KEY
    if (permissions.isEmpty()) {
      savePermissionResult(granted = true)
      finish()
      return
    }

    setContentView(permissionRequestView())
    requestPermissions.launch(permissions)
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

  private fun onPermissionResult(permissionResults: Map<String, Boolean>) {
    val granted: Boolean =
      permissions.all { permission: String ->
        return@all permissionResults[permission] == true
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
      .edit {
        putBoolean(preferenceKey, granted)
          .putBoolean(preferenceKey + ASKED_PREFIX, PERMISSION_ASKED)
      }
  }

  private companion object {
    private const val APP_PERMISSIONS: String = "PERMISSIONS"
    private const val ASKED_PREFIX: String = "_ASKED"
    private const val PERMISSION_ASKED: Boolean = true
    private const val PADDING_DP: Float = 24.0f
    private const val MESSAGE_TEXT_SP: Float = 16.0f
    private const val SCRIM_COLOR: Int = 0xDD000000.toInt()
  }
}

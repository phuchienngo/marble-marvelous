package com.phuchienngo.marblemarvelous.permissions

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.phuchienngo.marblemarvelous.R
import androidx.core.content.edit

class PermissionsActivity : ComponentActivity() {
    private var permissions: Array<String> = emptyArray()
    private var sharedPreferencesKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissions = intent.getStringArrayExtra(UserPermissions.PERMISSIONS_REQUESTED) ?: emptyArray()
        sharedPreferencesKey = intent.getStringExtra(UserPermissions.SHARED_PREF_KEY)
        if (sharedPreferencesKey == null) {
            finish()
            return
        }
        if (permissions.isEmpty()) {
            savePermissionResult(granted = true)
            finish()
            return
        }

        setContent permissionContent@{
            permissionRequestScreen(
                permissions = permissions,
                onPermissionResult = permissionRequestResult@{ permissionResults: Map<String, Boolean> ->
                    return@permissionRequestResult onPermissionResult(permissionResults)
                }
            )
            return@permissionContent
        }
    }

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
        val preferences: SharedPreferences = secureContext.getSharedPreferences(APP_PERMISSIONS, Context.MODE_PRIVATE)
        preferences
            .edit savePermissionResult@{
                putBoolean(preferenceKey, granted)
                    .putBoolean(preferenceKey + ASKED_PREFIX, PERMISSION_ASKED)
                return@savePermissionResult
            }
    }

    @Composable
    private fun permissionRequestScreen(
        permissions: Array<String>,
        onPermissionResult: (Map<String, Boolean>) -> Unit
    ) {
        val launcher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>> =
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
                onResult = { permissionResults: Map<String, Boolean> ->
                    return@rememberLauncherForActivityResult onPermissionResult(permissionResults)
                }
            )
        LaunchedEffect(Unit) launchPermissions@{
            launcher.launch(permissions)
            return@launchPermissions
        }
        permissionRequestContent()
    }

    @Composable
    private fun permissionRequestContent() {
        MaterialTheme(colorScheme = darkColorScheme()) permissionsTheme@{
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color(0xDD000000)
            ) permissionsSurface@{
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) permissionsBox@{
                    Text(
                        text = stringResource(R.string.permissions_request_message),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    return@permissionsBox
                }
                return@permissionsSurface
            }
            return@permissionsTheme
        }
    }

    private companion object {
        private const val APP_PERMISSIONS: String = "PERMISSIONS"
        private const val ASKED_PREFIX: String = "_ASKED"
        private const val PERMISSION_ASKED: Boolean = true
    }
}

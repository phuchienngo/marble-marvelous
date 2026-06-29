package com.phuchienngo.marblemarvelous.permissions

import android.content.Context

class LocationPermissions(
    context: Context,
    listener: PermissionsListener
) : UserPermissions(context, listener) {
    override fun getPermissions(): Array<String> = arrayOf(LOCATION_PERMISSION)

    override fun getSharedPrefKey(): String = LOCATION_KEY

    companion object {
        const val LOCATION_PERMISSION: String = "android.permission.ACCESS_FINE_LOCATION"
        const val LOCATION_KEY: String = "location"
    }
}

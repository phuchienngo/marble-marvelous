package com.phuchienngo.marblemarvelous.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.badlogic.gdx.math.Vector2
import com.phuchienngo.marblemarvelous.R
import com.phuchienngo.marblemarvelous.permissions.LocationPermissions
import com.phuchienngo.marblemarvelous.permissions.PermissionsListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.util.Locale
import java.util.Scanner
import java.util.TimeZone
import javax.inject.Inject

class UserLocationEarth
    @Inject
    constructor(
        private val context: Context,
    ) : PermissionsListener {
        private val locationManager: LocationManager =
            context.getSystemService("location") as LocationManager
        private val locationPermissions: LocationPermissions = LocationPermissions(context, this)
        private var permissionsAccepted: Boolean = locationPermissions.arePermissionsGranted()
        private var countries: JSONObject? = null

        @SuppressLint("MissingPermission")
        fun lastKnown(requestPermissions: Boolean): Vector2 {
            val preferences: SharedPreferences = getSharedPreferences()
            permissionsAccepted = locationPermissions.arePermissionsGranted()
            if (!permissionsAccepted) {
                if (requestPermissions && !locationPermissions.arePermissionsGranted() &&
                    !locationPermissions.werePermissionsAsked()
                ) {
                    locationPermissions.requestPermissions()
                }
                return getFallbackLocation()
            }
            val location: Location? = locationManager.getLastKnownLocation("passive")
            if (location != null) {
                val lat: Float = location.latitude.toFloat()
                val lng: Float = location.longitude.toFloat()
                val editor: SharedPreferences.Editor = preferences.edit()
                editor.putFloat(PREF_LAST_LNG, lng)
                editor.putFloat(PREF_LAST_LAT, lat)
                val committed: Boolean = editor.commit()
                if (!committed) {
                    Log.w(TAG, "Couldn't save last known location")
                }
                return Vector2(lng, lat)
            }
            return getCachedLocation(preferences) ?: getFallbackLocation()
        }

        private fun getCachedLocation(preferences: SharedPreferences): Vector2? {
            val longitude: Float = preferences.getFloat(PREF_LAST_LNG, 0.0f)
            val latitude: Float = preferences.getFloat(PREF_LAST_LAT, 0.0f)
            if (longitude != 0.0f || latitude != 0.0f) {
                return Vector2(longitude, latitude)
            }
            return null
        }

        private fun getFallbackLocation(): Vector2 {
            val timeZoneLocation: Vector2 = LocationFallback.fromTimeZone(TimeZone.getDefault(), System.currentTimeMillis())
            if (timeZoneLocation.x != GMT_LONGITUDE) {
                return timeZoneLocation
            }
            return getCountryFallbackLocation() ?: timeZoneLocation
        }

        private fun getCountryFallbackLocation(): Vector2? {
            return try {
                if (countries == null) {
                    context.resources.openRawResource(R.raw.countries).use countriesInput@{ inputStream: InputStream ->
                        Scanner(inputStream).use countriesScanner@{ scanner: Scanner ->
                            scanner.useDelimiter("\\A")
                            if (!scanner.hasNext()) {
                                throw RuntimeException("Cannot parse countries json")
                            }
                            countries = JSONObject(scanner.next())
                            return@countriesScanner
                        }
                        return@countriesInput
                    }
                }
                val locale: Locale =
                    context.resources.configuration.locales
                        .get(0)
                val country: String = locale.getISO3Country()
                val countryData: JSONArray = countries!!.getJSONArray(country)
                Vector2(countryData.getDouble(2).toFloat(), countryData.getDouble(1).toFloat())
            } catch (e: Exception) {
                Log.e(TAG, "Cannot load country fallback location", e)
                null
            }
        }

        override fun onPermissionsAccepted(key: String) {
            if (key == "location") {
                permissionsAccepted = true
            }
        }

        private fun getSharedPreferences() =
            context
                .createDeviceProtectedStorageContext()
                .getSharedPreferences("location", 0)

        companion object {
            private const val GMT_LONGITUDE: Float = 0.0f
            private const val PREF_LAST_LAT: String = "last_lat"
            private const val PREF_LAST_LNG: String = "last_lng"
            private const val TAG: String = "UserLocationEarth"
        }
    }

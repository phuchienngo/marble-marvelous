package com.phuchienngo.marblemarvelous.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.phuchienngo.marblemarvelous.R
import com.phuchienngo.marblemarvelous.permissions.LocationPermissions
import com.phuchienngo.marblemarvelous.permissions.PermissionsListener
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserLocationEarth
@Inject
constructor(
  @param:ApplicationContext private val context: Context
) : PermissionsListener {
  private val locationManager: LocationManager =
    context.getSystemService("location") as LocationManager
  private val locationPermissions: LocationPermissions = LocationPermissions(context, this)
  private var permissionsAccepted: Boolean = locationPermissions.arePermissionsGranted()
  private var countriesJson: String? = null

  @SuppressLint("MissingPermission")
  fun lastKnown(requestPermissions: Boolean): GeoLocation {
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
      preferences
        .edit()
        .putFloat(PREF_LAST_LNG, lng)
        .putFloat(PREF_LAST_LAT, lat)
        .apply()
      return GeoLocation(
        longitudeDegrees = lng,
        latitudeDegrees = lat
      )
    }
    return getCachedLocation(preferences) ?: getFallbackLocation()
  }

  private fun getCachedLocation(preferences: SharedPreferences): GeoLocation? {
    val longitude: Float = preferences.getFloat(PREF_LAST_LNG, 0.0f)
    val latitude: Float = preferences.getFloat(PREF_LAST_LAT, 0.0f)
    if (longitude != 0.0f || latitude != 0.0f) {
      return GeoLocation(
        longitudeDegrees = longitude,
        latitudeDegrees = latitude
      )
    }
    return null
  }

  private fun getFallbackLocation(): GeoLocation {
    val timeZoneLocation: GeoLocation =
      LocationFallback.fromTimeZone(TimeZone.getDefault(), System.currentTimeMillis())
    if (timeZoneLocation.longitudeDegrees != GMT_LONGITUDE) {
      return timeZoneLocation
    }
    return getCountryFallbackLocation() ?: timeZoneLocation
  }

  private fun getCountryFallbackLocation(): GeoLocation? {
    return try {
      if (countriesJson == null) {
        countriesJson =
          context.resources.openRawResource(R.raw.countries)
            .bufferedReader()
            .use { reader -> reader.readText() }
      }
      val locale: Locale =
        context.resources.configuration.locales
          .get(0)
      val country: String = locale.isO3Country
      parseCountryLocation(requireNotNull(countriesJson), country)
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
    internal fun parseCountryLocation(
      json: String,
      country: String
    ): GeoLocation? {
      val countryPattern =
        Regex(
          "\\\"${Regex.escape(country)}\\\"\\s*:\\s*\\[\\s*\\\"(?:\\\\.|[^\\\"])*\\\"\\s*," +
            "\\s*(-?\\d+(?:\\.\\d+)?)\\s*,\\s*(-?\\d+(?:\\.\\d+)?)"
        )
      val match: MatchResult = countryPattern.find(json) ?: return null
      val latitude: Float = match.groupValues[1].toFloatOrNull() ?: return null
      val longitude: Float = match.groupValues[2].toFloatOrNull() ?: return null
      return GeoLocation(longitudeDegrees = longitude, latitudeDegrees = latitude)
    }

    private const val GMT_LONGITUDE: Float = 0.0f
    private const val PREF_LAST_LAT: String = "last_lat"
    private const val PREF_LAST_LNG: String = "last_lng"
    private const val TAG: String = "UserLocationEarth"
  }
}

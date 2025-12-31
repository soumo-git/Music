package com.android.music.duo.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Helper class for checking and managing Duo (WiFi Direct) permissions
 */
object DuoPermissionHelper {

    /**
     * Get required permissions for WiFi Direct based on Android version
     */
    fun getRequiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ uses NEARBY_WIFI_DEVICES
            arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            // Android 12 and below
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    /**
     * Check if all required permissions are granted
     */
    fun hasRequiredPermissions(context: Context): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if WiFi is enabled
     */
    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled == true
    }

    /**
     * Check if location services are enabled
     */
    fun isLocationEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        return locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
               locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
    }

    /**
     * Get a user-friendly error message based on what's missing
     */
    fun getErrorMessage(context: Context): String? {
        return when {
            !hasRequiredPermissions(context) -> "Location permission is required for WiFi Direct"
            !isWifiEnabled(context) -> "Please enable WiFi to use Duo"
            !isLocationEnabled(context) -> "Please enable Location services to discover nearby devices"
            else -> null
        }
    }

    /**
     * Check if all requirements are met for WiFi Direct
     */
    fun canUseWifiDirect(context: Context): Boolean {
        return hasRequiredPermissions(context) && isWifiEnabled(context) && isLocationEnabled(context)
    }
}

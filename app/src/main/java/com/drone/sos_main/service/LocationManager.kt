
package com.drone.sos_main.service

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.drone.sos_main.network.SocketManager

/**
 * LocationManager
 *
 * Responsibilities:
 * - Get GPS location every 1 second
 * - Store last known location
 * - Stream live updates to backend while SOS is active
 *
 * Flow:
 * GPS → onLocationResult()
 *     → SocketManager.updateLocation()
 *     → Backend receives "update-location"
 */
class LocationManager(private val context: Context) {

    private val TAG = "LocationManager"

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var isStreaming = false

    // Last known coordinates (used when SOS starts)
    var lastLatitude: Double = 0.0
    var lastLongitude: Double = 0.0
    var lastAltitude: Double = 0.0

    // ─────────────────────────────────────────────
    // LOCATION REQUEST (1 second updates)
    // ─────────────────────────────────────────────
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        1000L
    ).apply {
        setMinUpdateIntervalMillis(1000L)
        setMinUpdateDistanceMeters(0f)
        setWaitForAccurateLocation(false)
    }.build()

    // ─────────────────────────────────────────────
    // LOCATION CALLBACK
    // ─────────────────────────────────────────────
    private val locationCallback = object : LocationCallback() {

        override fun onLocationResult(result: LocationResult) {
            val location: Location = result.lastLocation ?: return

            // Save latest location
            lastLatitude = location.latitude
            lastLongitude = location.longitude
            lastAltitude =
                if (location.hasAltitude()) location.altitude else 0.0

            Log.d(TAG, "Location: $lastLatitude, $lastLongitude")

            // Send live location to backend
            SocketManager.updateLocation(
                lat = lastLatitude,
                lon = lastLongitude,
                alt = lastAltitude
            )
        }
    }

    // ─────────────────────────────────────────────
    // START STREAMING
    // ─────────────────────────────────────────────
    @SuppressLint("MissingPermission") // permission handled in UI
    fun startStreaming() {

        if (isStreaming) return

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )

        isStreaming = true
        Log.d(TAG, "Location streaming started")
    }

    // ─────────────────────────────────────────────
    // STOP STREAMING
    // ─────────────────────────────────────────────
    fun stopStreaming() {

        if (!isStreaming) return

        fusedLocationClient.removeLocationUpdates(locationCallback)
        isStreaming = false

        Log.d(TAG, "Location streaming stopped")
    }

    fun isStreaming(): Boolean = isStreaming
}

package com.example.mw_watch_companion.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.mw_watch_companion.R
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

import com.example.mw_watch_companion.common.PermissionManager
import com.example.mw_watch_companion.OSM_backend.OSMBackend

class NavigationService : Service() {

    companion object {
        private const val TAG = "NavigationService"
        private const val CHANNEL_ID = "navigation_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.mw_watch_companion.START_NAVIGATION"
        const val ACTION_STOP = "com.example.mw_watch_companion.STOP_NAVIGATION"
        const val EXTRA_DEST_LAT = "destination_latitude"
        const val EXTRA_DEST_LON = "destination_longitude"
    }

    private val fusedLocationClient by lazy {
        LocationServices.getFusedLocationProviderClient(this)
    }

    private val permissionManager by lazy {
        PermissionManager(this)
    }

    private var destinationLatitude: Double? = null
    private var destinationLongitude: Double? = null
    private var tracking = false
    private var osmBackend: OSMBackend? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            osmBackend?.refreshData(
                latitude = location.latitude,
                longitude = location.longitude,
                bearingDegrees = if (location.hasBearing()) {
                    location.bearing
                } else { null }
            )
            //logLocation(location)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopLocationTracking()
                closeOsmBackend()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_START -> {
                stopLocationTracking()
                closeOsmBackend()
                destinationLatitude = intent.getDoubleExtra(EXTRA_DEST_LAT, Double.NaN)
                destinationLongitude = intent.getDoubleExtra(EXTRA_DEST_LON, Double.NaN)

                if (!validDestination()) {
                    Log.e(TAG, "Invalid destination coordinates")
                    stopSelf()
                    return START_NOT_STICKY
                }

                startForegroundNotification()

                if (!permissionManager.hasLocationPermission()) {
                    Log.e(TAG, "Location permission not granted")
                    closeOsmBackend()
                    stopSelf()
                    return START_NOT_STICKY
                }
                osmBackend = OSMBackend(
                    destinationLatitude = destinationLatitude!!,
                    destinationLongitude = destinationLongitude!!
                )
                startLocationTracking()
            }

            else -> {
                stopSelf()
                closeOsmBackend()
                return START_NOT_STICKY
            }
        }

        return START_STICKY
    }

    private fun validDestination(): Boolean {
        val lat = destinationLatitude ?: return false
        val lon = destinationLongitude ?: return false
        return lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0
    }

    private fun startLocationTracking() {
        if (tracking) return

        if (!permissionManager.hasLocationPermission()) {
            closeOsmBackend()
            stopSelf()
            return
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1_025L
        )
            .setMinUpdateIntervalMillis(1_025L)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                mainLooper
            ).addOnSuccessListener {
                tracking = true
                Log.i(TAG, "Location tracking started")
            }.addOnFailureListener { error ->
                Log.e(TAG, "Unable to start location tracking", error)
                closeOsmBackend()
                stopSelf()
            }
        } catch (error: SecurityException) {
            Log.e(TAG, "Location permission was revoked", error)
            closeOsmBackend()
            stopSelf()
        }
    }

    private fun logLocation(location: Location) {
        val hasBearing = location.hasBearing()
        val hasBearingAccuracy =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                location.hasBearingAccuracy()

        Log.i(
            TAG,
            "lat=${location.latitude}, " +
                "lon=${location.longitude}, " +
                "accuracyM=${location.accuracy}, " +
                "speedMps=${if (location.hasSpeed()) location.speed else null}, " +
                "bearingDeg=${if (hasBearing) location.bearing else null}, " +
                "bearingAccuracyDeg=${
                    if (hasBearingAccuracy) location.bearingAccuracyDegrees else null
                }, " +
                "time=${location.time}"
        )
    }

    private fun stopLocationTracking() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        tracking = false
    }

    private fun startForegroundNotification() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Navigation active")
            .setContentText("Getting current location")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Navigation",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun closeOsmBackend() {
        osmBackend?.close()
        osmBackend = null
    }

    override fun onDestroy() {
        stopLocationTracking()
        closeOsmBackend()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

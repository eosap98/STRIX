package com.strix.safesync.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.android.gms.location.*

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        createNotificationChannel()
        val notification = Notification.Builder(this, "LocationChannel")
            .setContentTitle("SafeSync Tracking")
            .setContentText("Real-time location sharing active")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        
        startForeground(1, notification)
        
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateIntervalMillis(5000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    Log.d("SafeSyncLocation", "Location update: ${location.latitude}, ${location.longitude}")
                    com.strix.safesync.data.FirebaseManager.uploadLocation(location.latitude, location.longitude)
                    com.strix.safesync.data.FirebaseManager.uploadHeartbeat()
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } catch (unlikely: SecurityException) {
            Log.e("SafeSyncLocation", "Lost location permission. Couldn't remove updates. $unlikely")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "LocationChannel",
            "Location Service Channel",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}

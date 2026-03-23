package com.strix.safesync

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class SafeSyncApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initCustomFirebase()
    }

    private fun initCustomFirebase() {
        if (FirebaseApp.getApps(this).isNotEmpty()) return

        val prefs = getSharedPreferences("SafeSyncCustomServer", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("apiKey", "")
        val appId = prefs.getString("appId", "")
        val projectId = prefs.getString("projectId", "")
        val storageBucket = prefs.getString("storageBucket", "")

        if (!apiKey.isNullOrBlank() && !appId.isNullOrBlank() && !projectId.isNullOrBlank()) {
            try {
                val options = FirebaseOptions.Builder()
                    .setApiKey(apiKey)
                    .setApplicationId(appId)
                    .setProjectId(projectId)
                    .setStorageBucket(if (storageBucket.isNullOrBlank()) "$projectId.appspot.com" else storageBucket)
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.d("SafeSyncApp", "Initialized CUSTOM Firebase project: $projectId")
            } catch (e: Exception) {
                Log.e("SafeSyncApp", "Failed to init custom Firebase", e)
                FirebaseApp.initializeApp(this)
            }
        } else {
            try {
                FirebaseApp.initializeApp(this)
                Log.d("SafeSyncApp", "Initialized DEFAULT Firebase project")
            } catch (e: Exception) {
                Log.e("SafeSyncApp", "Failed to init default Firebase. Missing google-services.json?", e)
            }
        }
    }
}

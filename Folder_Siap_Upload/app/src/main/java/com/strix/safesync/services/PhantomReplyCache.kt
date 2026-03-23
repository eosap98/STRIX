package com.strix.safesync.services

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log

object PhantomReplyCache {
    private val actionCache = mutableMapOf<String, ReplyAction>()

    data class ReplyAction(
        val pendingIntent: PendingIntent,
        val remoteInput: RemoteInput
    )

    fun saveReplyAction(chatKey: String, pendingIntent: PendingIntent, remoteInput: RemoteInput) {
        actionCache[chatKey] = ReplyAction(pendingIntent, remoteInput)
    }

    fun executeReply(context: Context, chatKey: String, messageText: String): Boolean {
        val action = actionCache[chatKey]
        if (action == null) {
            Log.e("SafeSyncPhantom", "No reply action found for: $chatKey")
            return false
        }

        try {
            val replyIntent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(action.remoteInput.resultKey, messageText)
            RemoteInput.addResultsToIntent(arrayOf(action.remoteInput), replyIntent, bundle)
            
            action.pendingIntent.send(context, 0, replyIntent)
            Log.d("SafeSyncPhantom", "Successfully Phantom-Replied to $chatKey")
            return true
        } catch (e: Exception) {
            Log.e("SafeSyncPhantom", "Failed to send Phantom Reply: ${e.message}")
            return false
        }
    }
}

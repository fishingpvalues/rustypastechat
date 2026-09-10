package com.rustypastechat.data.sync

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rustypastechat.MainActivity
import com.rustypastechat.R
import com.rustypastechat.data.model.Message
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts a local notification per chat that gained messages since the last
 * sync, grouped under one summary so twelve new pastes are one shade entry
 * and not twelve.
 *
 * Everything here is local: rustypaste has no push channel, so "new message"
 * is whatever [ChatSyncWorker] found by diffing the file listing.
 */
@Singleton
class MessageNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID = "new_messages"
        const val GROUP_KEY = "com.rustypastechat.NEW_MESSAGES"
        private const val SUMMARY_ID = 1
        const val EXTRA_CHAT_ID = "chat_id"
    }

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_messages),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_messages_desc)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPost(): Boolean {
        // POST_NOTIFICATIONS only exists from API 33; below that the permission
        // is implicit and the only gate is the user's channel setting.
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && manager.areNotificationsEnabled()
    }

    /**
     * @param chatName human-readable chat title
     * @param newMessages the messages that arrived since the last sync, oldest first
     */
    // canPost() above is the permission check, and the notify calls below are
    // additionally wrapped in runCatching - which is the "explicitly handle a
    // potential SecurityException" half of what lint asks for. Lint cannot
    // follow the check through a helper method, so state it here rather than
    // duplicating the check inline.
    @SuppressLint("MissingPermission")
    fun notifyChat(chatId: String, chatName: String, newMessages: List<Message>) {
        if (newMessages.isEmpty() || !canPost()) return
        ensureChannel()

        val style = NotificationCompat.InboxStyle().setBigContentTitle(chatName)
        newMessages.takeLast(5).forEach { style.addLine(previewOf(it)) }
        if (newMessages.size > 5) {
            style.setSummaryText("+${newMessages.size - 5} more")
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(chatName)
            .setContentText(previewOf(newMessages.last()))
            .setStyle(style)
            .setNumber(newMessages.size)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openChatIntent(chatId))

        runCatching {
            manager.notify(chatId.hashCode(), builder.build())
            manager.notify(SUMMARY_ID, summary())
        }
    }

    fun cancelChat(chatId: String) {
        runCatching { manager.cancel(chatId.hashCode()) }
    }

    private fun summary(): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_summary_title))
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(openChatIntent(null))
            .build()

    private fun openChatIntent(chatId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (chatId != null) putExtra(EXTRA_CHAT_ID, chatId)
        }
        return PendingIntent.getActivity(
            context,
            chatId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun previewOf(message: Message): String = when {
        message.text.isNotBlank() -> message.text.take(120)
        message.mediaType != null -> "📎 ${message.mediaType.name.lowercase()}"
        else -> context.getString(R.string.notification_new_paste)
    }
}

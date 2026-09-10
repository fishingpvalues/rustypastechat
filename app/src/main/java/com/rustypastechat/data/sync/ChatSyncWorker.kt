package com.rustypastechat.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rustypastechat.data.local.ChatMetadataStore
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.Message
import com.rustypastechat.data.repository.PasteRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Polls the rustypaste file listing and raises a local notification for every
 * chat that gained a message this device did not write.
 *
 * This is the whole "the app feels alive" mechanism. rustypaste has no push
 * channel and no session concept, so there is nothing to subscribe to - a
 * periodic diff of the listing is the only thing available, and WorkManager's
 * 15-minute floor is the tightest interval the platform will honour for
 * periodic work.
 *
 * Two things it deliberately does NOT do:
 *  - notify for outgoing messages (this device wrote them),
 *  - notify for archived or muted chats.
 */
@HiltWorker
class ChatSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val preferencesManager: PreferencesManager,
    private val chatMetadataStore: ChatMetadataStore,
    private val pasteRepository: PasteRepository,
    private val notifier: MessageNotifier
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = preferencesManager.settingsFlow.first()
        if (settings.pasteServerUrl.isBlank()) return Result.success()
        if (!settings.backgroundSyncEnabled) return Result.success()

        val messages = pasteRepository.loadAllMessages().getOrElse {
            // A server that is briefly unreachable is the normal case for a
            // phone; retry rather than reporting failure, which WorkManager
            // would otherwise count against the job.
            return Result.retry()
        }

        val meta = chatMetadataStore.all()
        val byChat: Map<String, List<Message>> = messages.groupBy { it.chatId }

        for ((chatId, chatMessages) in byChat) {
            val m = meta[chatId]
            if (m?.archived == true || m?.muted == true) continue

            // Identity, not time: an untimestamped legacy paste is restamped
            // with the current clock on every load, so a timestamp cursor
            // would re-announce the entire chat on every single sync.
            val seen = (m?.readIds ?: emptySet()) + (m?.notifiedIds ?: emptySet())
            val fresh = chatMessages
                .filter { !it.isOutgoing && it.id !in seen }
                .sortedBy { it.timestamp }
            if (fresh.isEmpty()) continue

            notifier.notifyChat(
                chatId = chatId,
                chatName = m?.name ?: defaultChatName(chatId),
                newMessages = fresh
            )
            val presentIds = chatMessages.map { it.id }.toSet()
            chatMetadataStore.update(chatId) {
                // Intersect with what the server still holds, so the set
                // cannot grow without bound as pastes expire.
                it.copy(notifiedIds = (it.notifiedIds + fresh.map { f -> f.id }) intersect presentIds)
            }
        }
        return Result.success()
    }

    private fun defaultChatName(chatId: String): String =
        if (chatId == Message.DEFAULT_CHAT) "General" else "Chat $chatId"

    companion object {
        const val WORK_NAME = "chat-sync"
    }
}

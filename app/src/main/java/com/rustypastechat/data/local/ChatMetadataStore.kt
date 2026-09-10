package com.rustypastechat.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rustypastechat.data.model.ChatCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything about a chat that the rustypaste server cannot store.
 *
 * A "chat" on the server is only a filename prefix - there is no room in it
 * for a display name, a category, a colour or a read marker. Before this
 * store those lived in a `mutableListOf<ChatThread>` inside
 * `ChatListViewModel`, i.e. in process memory: renaming a chat, giving it a
 * category or deleting it survived exactly until the next process death, and
 * `unreadCount` was rendered as a badge that no code ever set above zero.
 */
@Serializable
data class ChatMeta(
    val name: String? = null,
    val category: String? = null,
    val avatarColor: Long? = null,
    val archived: Boolean = false,
    val muted: Boolean = false,
    /** Timestamp of the newest message the user has actually seen in this chat.
     *  Kept for display/ordering; [readIds] is what the badge counts. */
    val lastReadTimestamp: Long = 0L,
    /**
     * Ids (paste filenames) of the messages the user has already seen.
     *
     * The badge cannot key on a timestamp here. A legacy paste carries no
     * time anywhere - the filename has none and this server returns
     * `creation_date_utc: null` - so `pasteToImportedMessage` falls back to
     * `System.currentTimeMillis()`, i.e. every reload stamps those messages
     * NOW. Against a timestamp marker they are therefore unread again one
     * second after being read: measured on the live server, the badge sat at
     * 30 and then 31 across opening the chat. Filenames are stable, so
     * identity works where time does not.
     *
     * Rewritten from the current message list on every read, so it stays
     * bounded by what the server actually holds.
     */
    val readIds: Set<String> = emptySet(),
    /** Ids already announced in a notification, so a sync does not re-fire them. */
    val notifiedIds: Set<String> = emptySet(),
    /** Set by "delete chat" for a chat whose pastes could not be removed server-side. */
    val hidden: Boolean = false
) {
    val categoryOrDefault: ChatCategory
        get() = category?.let { runCatching { ChatCategory.valueOf(it) }.getOrNull() } ?: ChatCategory.GENERAL
}

private val Context.chatMetaStore: DataStore<Preferences> by preferencesDataStore(name = "chat_metadata")

@Singleton
class ChatMetadataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        val KEY_META = stringPreferencesKey("chat_meta_json")
        // Unknown keys are ignored so an older build reading a newer store
        // degrades to defaults instead of throwing away every chat name.
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        // Named explicitly rather than relying on the reified
        // encodeToString/decodeFromString overloads: with a Map the compiler
        // picks the (SerializationStrategy, value) overload instead and the
        // call does not compile.
        val MAP_SERIALIZER = MapSerializer(String.serializer(), ChatMeta.serializer())
    }

    val metaFlow: Flow<Map<String, ChatMeta>> = context.chatMetaStore.data.map { prefs ->
        decode(prefs[KEY_META])
    }

    suspend fun get(chatId: String): ChatMeta = metaFlow.first()[chatId] ?: ChatMeta()

    suspend fun all(): Map<String, ChatMeta> = metaFlow.first()

    /** Read-modify-write of one chat's entry inside DataStore's own edit transaction. */
    suspend fun update(chatId: String, transform: (ChatMeta) -> ChatMeta) {
        context.chatMetaStore.edit { prefs ->
            val current = decode(prefs[KEY_META]).toMutableMap()
            current[chatId] = transform(current[chatId] ?: ChatMeta())
            prefs[KEY_META] = JSON.encodeToString(MAP_SERIALIZER, current.toMap())
        }
    }

    suspend fun remove(chatId: String) {
        context.chatMetaStore.edit { prefs ->
            val current = decode(prefs[KEY_META]).toMutableMap()
            current.remove(chatId)
            prefs[KEY_META] = JSON.encodeToString(MAP_SERIALIZER, current.toMap())
        }
    }

    private fun decode(raw: String?): Map<String, ChatMeta> {
        if (raw.isNullOrBlank()) return emptyMap()
        // A corrupt blob must not brick the chat list; losing the metadata is
        // recoverable (names fall back to "Chat <id>"), a crash loop is not.
        return runCatching { JSON.decodeFromString(MAP_SERIALIZER, raw) }.getOrDefault(emptyMap())
    }
}

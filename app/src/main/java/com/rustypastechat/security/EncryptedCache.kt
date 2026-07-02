package com.rustypastechat.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedCache @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheDir: File
        get() = File(context.filesDir, "encrypted_cache").also { if (!it.exists()) it.mkdirs() }

    private val messagesDir: File
        get() = File(cacheDir, "messages").also { if (!it.exists()) it.mkdirs() }

    private val mediaDir: File
        get() = File(context.cacheDir, "media").also { if (!it.exists()) it.mkdirs() }

    suspend fun writeMessage(chatId: String, messageId: String, content: String) {
        withContext(Dispatchers.IO) {
            val file = File(messagesDir, "${chatId}_${messageId}.enc")
            VaultCrypto.createEncryptedFile(context, file).openFileOutput()
                .bufferedWriter().use { it.write(content) }
        }
    }

    suspend fun readMessage(chatId: String, messageId: String): String? {
        return withContext(Dispatchers.IO) {
            val file = File(messagesDir, "${chatId}_${messageId}.enc")
            if (!file.exists()) return@withContext null
            try {
                VaultCrypto.createEncryptedFile(context, file).openFileInput()
                    .bufferedReader().use { it.readText() }
            } catch (_: Exception) { null }
        }
    }

    suspend fun writeMedia(mediaFileName: String, data: ByteArray) {
        withContext(Dispatchers.IO) {
            val file = File(mediaDir, "$mediaFileName.enc")
            VaultCrypto.createEncryptedFile(context, file).openFileOutput()
                .use { it.write(data) }
        }
    }

    suspend fun readMedia(mediaFileName: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            val file = File(mediaDir, "$mediaFileName.enc")
            if (!file.exists()) return@withContext null
            try {
                VaultCrypto.createEncryptedFile(context, file).openFileInput()
                    .use { it.readBytes() }
            } catch (_: Exception) { null }
        }
    }

    suspend fun deleteMessage(chatId: String, messageId: String) {
        withContext(Dispatchers.IO) {
            File(messagesDir, "${chatId}_${messageId}.enc").delete()
        }
    }

    suspend fun deleteMedia(mediaFileName: String) {
        withContext(Dispatchers.IO) {
            File(mediaDir, "$mediaFileName.enc").delete()
        }
    }

    fun getCacheSizeBytes(): Long {
        fun File.sizeRecursive(): Long =
            if (isDirectory) listFiles()?.sumOf { it.sizeRecursive() } ?: 0 else length()
        return cacheDir.sizeRecursive() + mediaDir.sizeRecursive()
    }

    suspend fun clearAll() {
        withContext(Dispatchers.IO) {
            cacheDir.deleteRecursively()
            mediaDir.deleteRecursively()
        }
    }
}

package com.rustypastechat.data.backup

import android.content.Context
import android.net.Uri
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.ChatThread
import com.rustypastechat.security.VaultCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val backupDir = File(context.filesDir, "backups").also { it.mkdirs() }

    suspend fun createBackup(
        chats: List<ChatThread>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val settings = runBlocking { preferencesManager.settingsFlow.first() }
            val serializableSettings = SerializableSettings.fromAppSettings(settings)

            val payload = BackupPayload(
                settings = serializableSettings,
                chats = chats.map { SerializableChat.fromChatThread(it) }
            )

            val jsonString = json.encodeToString(payload)
            val checksum = MessageDigest.getInstance("SHA-256")
                .digest(jsonString.toByteArray())
                .joinToString("") { "%02x".format(it) }

            val signedPayload = json.encodeToString(
                SignedBackup(data = jsonString, checksum = checksum)
            )

            val encryptedFile = VaultCrypto.createEncryptedFile(context, outputFile)
            encryptedFile.openFileOutput().bufferedWriter().use { it.write(signedPayload) }
            outputFile
        }
    }

    suspend fun restoreBackup(inputFile: File): Result<BackupPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val encryptedFile = VaultCrypto.createEncryptedFile(context, inputFile)
            val content = encryptedFile.openFileInput().bufferedReader().use { it.readText() }
            val signed = json.decodeFromString<SignedBackup>(content)

            val computedChecksum = MessageDigest.getInstance("SHA-256")
                .digest(signed.data.toByteArray())
                .joinToString("") { "%02x".format(it) }

            if (computedChecksum != signed.checksum) {
                throw SecurityException("Backup integrity check failed — checksum mismatch")
            }
            json.decodeFromString<BackupPayload>(signed.data)
        }
    }

    suspend fun restoreBackupFromUri(uri: Uri): Result<BackupPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val tempFile = File(context.cacheDir, "restore_temp.rpbackup")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: throw Exception("Cannot read backup file")
            val result = restoreBackup(tempFile)
            tempFile.delete()
            result.getOrThrow()
        }
    }

    fun createShareableBackup(cacheFile: File): Uri {
        val dateStr = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val outputFile = File(backupDir, "rustypaste-backup-$dateStr.rpbackup")
        cacheFile.copyTo(outputFile, overwrite = true)
        return androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", outputFile
        )
    }

    fun listBackups(): List<File> = backupDir.listFiles()
        ?.filter { it.name.endsWith(".rpbackup") }
        ?.sortedByDescending { it.lastModified() }
        ?: emptyList()

    fun getBackupAge(file: File): String {
        val diff = System.currentTimeMillis() - file.lastModified()
        return when {
            diff < 60_000 -> "Just now"
            diff < 3_600_000 -> "${diff / 60_000} min ago"
            diff < 86_400_000 -> "${diff / 3_600_000} h ago"
            else -> "${diff / 86_400_000} d ago"
        }
    }

    fun deleteBackup(file: File) { file.delete() }

    fun getCacheDir(): File = context.cacheDir

    @Serializable
    private data class SignedBackup(val data: String, val checksum: String)
}

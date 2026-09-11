package com.rustypastechat.security

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Key material for the two at-rest stores: EncryptedSharedPreferences for the
 * auth token and the LLM key, EncryptedFile for cached message bodies and
 * backup archives. Both are bound to a Keystore-backed [MasterKey], which is
 * why the app opts out of Android backup entirely - see
 * res/xml/data_extraction_rules.xml.
 *
 * This deliberately offers no raw Cipher helpers. It used to, and they were a
 * trap: the key came from an in-process KeyGenerator rather than the Keystore,
 * behind a private function named getAesKeyFromKeystore, so anything encrypted
 * with them became undecryptable at the next process death. Nothing ever
 * called them. Encrypt through EncryptedFile or EncryptedSharedPreferences, or
 * add a Keystore-backed key here with a test that survives a restart.
 */
object VaultCrypto {

    const val KEY_ALIAS = "rustypastechat_secrets_v1"
    private const val META_PREFS = "rustypastechat_secure_prefs"

    private var cachedMasterKey: MasterKey? = null

    fun getMasterKey(context: Context): MasterKey {
        return cachedMasterKey ?: MasterKey.Builder(context, KEY_ALIAS)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build().also { cachedMasterKey = it }
    }

    fun getOrCreateSecurePreferences(context: Context): EncryptedSharedPreferences {
        val masterKey = getMasterKey(context)
        return EncryptedSharedPreferences.create(
            context,
            META_PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }

    fun createEncryptedFile(
        context: Context,
        file: File
    ): EncryptedFile = EncryptedFile.Builder(
        context,
        file,
        getMasterKey(context),
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    fun createEncryptedFileInDir(
        context: Context,
        dir: File,
        fileName: String
    ): EncryptedFile {
        if (!dir.exists()) dir.mkdirs()
        return createEncryptedFile(context, File(dir, fileName))
    }
}

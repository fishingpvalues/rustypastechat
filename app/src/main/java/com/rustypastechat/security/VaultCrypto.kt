package com.rustypastechat.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

object VaultCrypto {

    private const val KEY_ALIAS = "rustypastechat_secrets_v1"
    private const val META_PREFS = "rustypastechat_secure_prefs"

    fun getMasterKey(context: Context): MasterKey = MasterKey.Builder(context, KEY_ALIAS)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

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

    fun generateRandomToken(length: Int = 32): String {
        val bytes = ByteArray(length).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}

package com.rustypastechat.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object VaultCrypto {

    const val KEY_ALIAS = "rustypastechat_secrets_v1"
    private const val META_PREFS = "rustypastechat_secure_prefs"
    private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

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

    fun generateRandomToken(length: Int = 32): String {
        val bytes = ByteArray(length).also { SecureRandom().nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
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

    fun getEncryptionCipher(): Cipher {
        val key = generateAesKey()
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    fun getDecryptionCipher(iv: ByteArray): Cipher {
        val key = getAesKeyFromKeystore()
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher
    }

    private var aesKey: SecretKey? = null

    private fun generateAesKey(): SecretKey {
        return aesKey ?: run {
            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256, SecureRandom())
            keyGenerator.generateKey().also { aesKey = it }
        }
    }

    private fun getAesKeyFromKeystore(): SecretKey {
        return aesKey ?: generateAesKey()
    }
}

package com.rustypastechat.data.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class SftpConfig(
    val host: String,
    val port: Int = 22,
    val username: String,
    val password: String = "",
    val privateKeyPath: String = "",
    val remotePath: String = "/",
    val fingerprint: String = ""
)

@Singleton
class SftpUploader @Inject constructor() {

    suspend fun upload(
        localFile: File,
        config: SftpConfig,
        onProgress: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        // JSch library not yet integrated (META-INF merge conflicts).
        // SFTP will be enabled in a future release.
        Result.failure(Exception("SFTP upload disabled in this build"))
    }

    suspend fun testConnection(config: SftpConfig): Result<String> = withContext(Dispatchers.IO) {
        Result.failure(Exception("SFTP test disabled in this build"))
    }
}

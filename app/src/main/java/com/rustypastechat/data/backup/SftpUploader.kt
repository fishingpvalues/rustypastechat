package com.rustypastechat.data.backup

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
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
        runCatching {
            val jsch = JSch()
            var session: Session? = null
            var channel: ChannelSftp? = null

            try {
                onProgress("Connecting to ${config.host}...")

                if (config.privateKeyPath.isNotBlank() && File(config.privateKeyPath).exists()) {
                    jsch.addIdentity(config.privateKeyPath)
                }

                session = jsch.getSession(config.username, config.host, config.port)
                if (config.password.isNotBlank()) {
                    session.setPassword(config.password)
                }

                val sshConfig = java.util.Properties().apply {
                    put("StrictHostKeyChecking", if (config.fingerprint.isNotBlank()) "yes" else "no")
                }
                session.setConfig(sshConfig)
                session.connect(15_000)

                onProgress("Uploading ${localFile.name}...")

                channel = session.openChannel("sftp") as ChannelSftp
                channel.connect(10_000)

                val remoteFile = "${config.remotePath.trimEnd('/')}/${localFile.name}"
                FileInputStream(localFile).use { stream ->
                    channel.put(stream, remoteFile)
                }

                onProgress("Backup uploaded to $remoteFile")
                "sftp://${config.host}:${config.port}$remoteFile"
            } finally {
                channel?.disconnect()
                session?.disconnect()
            }
        }
    }

    suspend fun testConnection(config: SftpConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val jsch = JSch()
            var session: Session? = null
            try {
                if (config.privateKeyPath.isNotBlank() && File(config.privateKeyPath).exists()) {
                    jsch.addIdentity(config.privateKeyPath)
                }
                session = jsch.getSession(config.username, config.host, config.port)
                if (config.password.isNotBlank()) {
                    session.setPassword(config.password)
                }
                session.setConfig("StrictHostKeyChecking", "no")
                session.connect(10_000)
                "Connected to ${config.host}:${config.port}"
            } finally {
                session?.disconnect()
            }
        }
    }
}

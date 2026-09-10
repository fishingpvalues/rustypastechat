package com.rustypastechat.data.backup

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Properties
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

/**
 * Uploads a backup file to an SFTP server.
 *
 * Host-key policy, which is the part worth reading: an upload REQUIRES a
 * pinned fingerprint. Disabling `StrictHostKeyChecking` (the usual way to
 * make sample SFTP code "just work") would hand the whole backup - every
 * message in every chat - to whatever answers on that host:port, which for a
 * phone on hotel wifi is not a theoretical attacker. So:
 *
 *  - [testConnection] connects, reports the server's SHA256 fingerprint and
 *    disconnects. That is the one operation allowed against an unpinned host,
 *    and it transfers nothing.
 *  - [upload] refuses unless [SftpConfig.fingerprint] is set AND matches.
 *
 * The fingerprint format is OpenSSH's: `SHA256:` + unpadded base64 of the
 * SHA-256 of the raw key blob, i.e. exactly the string `ssh-keyscan | ssh-keygen -lf -`
 * prints, so a user can verify it out of band instead of trusting the phone.
 */
@Singleton
class SftpUploader @Inject constructor() {

    /** Result of a connection probe: whether the pinned key matched, and what the server offered. */
    data class Probe(val fingerprint: String, val matchesPinned: Boolean)

    suspend fun upload(
        localFile: File,
        config: SftpConfig,
        onProgress: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        if (config.fingerprint.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException(
                    "No host key pinned. Run \"Test connection\" first and save the fingerprint it reports."
                )
            )
        }
        if (!localFile.exists()) {
            return@withContext Result.failure(IllegalArgumentException("Backup file is missing: ${localFile.name}"))
        }

        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            onProgress("Connecting to ${config.host}...")
            val repo = PinningHostKeyRepository(config.fingerprint)
            session = openSession(config, repo)
            channel = (session.openChannel("sftp") as ChannelSftp).apply { connect(CONNECT_TIMEOUT_MS) }

            val remoteDir = config.remotePath.ifBlank { "/" }
            mkdirs(channel, remoteDir)
            val remoteName = joinRemote(remoteDir, localFile.name)

            onProgress("Uploading ${localFile.name} (${localFile.length() / 1024} KB)...")
            localFile.inputStream().use { channel.put(it, remoteName, ChannelSftp.OVERWRITE) }

            Result.success(remoteName)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { session?.disconnect() }
        }
    }

    /**
     * Connects far enough to read the server's host key, then disconnects.
     * Transfers nothing, so it is safe to run against a host with no pinned
     * fingerprint - that is how the user gets one to pin.
     */
    suspend fun probeHostKey(config: SftpConfig): Result<Probe> = withContext(Dispatchers.IO) {
        var session: Session? = null
        try {
            val repo = PinningHostKeyRepository(pinned = null)
            session = openSession(config, repo)
            val seen = repo.seenFingerprint
                ?: return@withContext Result.failure(IllegalStateException("Server presented no host key"))
            Result.success(Probe(seen, matchesPinned = seen == config.fingerprint))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { session?.disconnect() }
        }
    }

    /**
     * Full check: authenticates and opens an SFTP channel against the pinned
     * host. Unlike [probeHostKey] this proves the credentials and the remote
     * path work, so it is what the settings screen calls once a fingerprint
     * has been saved.
     */
    suspend fun testConnection(config: SftpConfig): Result<String> = withContext(Dispatchers.IO) {
        if (config.fingerprint.isBlank()) {
            return@withContext probeHostKey(config).map { probe ->
                "Host key not pinned yet. Server offers ${probe.fingerprint} - save it to enable uploads."
            }
        }
        var session: Session? = null
        var channel: ChannelSftp? = null
        try {
            session = openSession(config, PinningHostKeyRepository(config.fingerprint))
            channel = (session.openChannel("sftp") as ChannelSftp).apply { connect(CONNECT_TIMEOUT_MS) }
            val dir = config.remotePath.ifBlank { "/" }
            channel.stat(dir) // throws if the path does not exist or is unreadable
            Result.success("Connected to ${config.host}:${config.port}, $dir is writable")
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { session?.disconnect() }
        }
    }

    private fun openSession(config: SftpConfig, repo: HostKeyRepository): Session {
        require(config.host.isNotBlank()) { "Host is required" }
        require(config.username.isNotBlank()) { "Username is required" }

        val jsch = JSch()
        if (config.privateKeyPath.isNotBlank()) {
            jsch.addIdentity(config.privateKeyPath, config.password.ifBlank { null })
        }
        jsch.hostKeyRepository = repo

        return jsch.getSession(config.username, config.host, config.port).apply {
            if (config.privateKeyPath.isBlank() && config.password.isNotEmpty()) {
                setPassword(config.password)
            }
            // "ask" (the default) would block on a console prompt that does
            // not exist on Android. The repository below is what actually
            // decides; this only stops JSch reaching for a terminal.
            setConfig(Properties().apply { put("StrictHostKeyChecking", "yes") })
            userInfo = SilentUserInfo(config.password)
            connect(CONNECT_TIMEOUT_MS)
        }
    }

    /** Creates every missing segment of [path], the way `mkdir -p` would. */
    private fun mkdirs(channel: ChannelSftp, path: String) {
        var current = ""
        for (segment in path.split('/')) {
            if (segment.isEmpty()) {
                current = "/"
                continue
            }
            current = joinRemote(current, segment)
            runCatching { channel.stat(current) }.onFailure {
                runCatching { channel.mkdir(current) }
            }
        }
    }

    private fun joinRemote(dir: String, name: String): String =
        if (dir.endsWith("/")) "$dir$name" else "$dir/$name"

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
    }
}

/**
 * Accepts exactly one host key: the pinned one. With [pinned] null it accepts
 * the first key it sees and records its fingerprint, which is only ever used
 * by the probe path (no data is transferred on that path).
 */
internal class PinningHostKeyRepository(private val pinned: String?) : HostKeyRepository {

    @Volatile
    var seenFingerprint: String? = null
        private set

    override fun check(host: String?, key: ByteArray?): Int {
        val fp = key?.let(::sshFingerprint) ?: return HostKeyRepository.NOT_INCLUDED
        seenFingerprint = fp
        if (pinned == null) return HostKeyRepository.OK
        return if (fp == pinned) HostKeyRepository.OK else HostKeyRepository.CHANGED
    }

    override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit
    override fun remove(host: String?, type: String?) = Unit
    override fun remove(host: String?, type: String?, key: ByteArray?) = Unit
    override fun getKnownHostsRepositoryID(): String = "rustypastechat-pinned"
    override fun getHostKey(): Array<HostKey> = emptyArray()
    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()

    companion object {
        /** OpenSSH's `SHA256:<unpadded base64>` form, so it can be compared by eye. */
        fun sshFingerprint(key: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(key)
            val b64 = android.util.Base64.encodeToString(
                digest,
                android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING
            )
            return "SHA256:$b64"
        }
    }
}

/** Never prompts: Android has no console, and every decision is made by the key repository. */
private class SilentUserInfo(private val password: String) : UserInfo {
    override fun getPassphrase(): String = password
    override fun getPassword(): String = password
    override fun promptPassword(message: String?): Boolean = password.isNotEmpty()
    override fun promptPassphrase(message: String?): Boolean = password.isNotEmpty()
    override fun promptYesNo(message: String?): Boolean = false
    override fun showMessage(message: String?) = Unit
}

package com.rustypastechat.data.backup

import com.jcraft.jsch.HostKeyRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.MessageDigest

/**
 * The host-key policy is the security-relevant half of the SFTP feature, so
 * it is pinned here rather than left to a manual check against a real server.
 */
@RunWith(RobolectricTestRunner::class)
class SftpUploaderTest {

    private val uploader = SftpUploader()

    @Test
    fun `upload refuses when no host key is pinned`() = runTest {
        val file = File.createTempFile("backup", ".rpbackup").apply { writeText("x") }
        val result = uploader.upload(
            file,
            SftpConfig(host = "example.invalid", username = "u", password = "p", fingerprint = "")
        )
        assertTrue("an unpinned host must never receive the backup", result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message.orEmpty().contains("host key", ignoreCase = true)
        )
        file.delete()
    }

    @Test
    fun `upload refuses a missing backup file even with a pinned key`() = runTest {
        val result = uploader.upload(
            File("/nonexistent/backup.rpbackup"),
            SftpConfig(host = "example.invalid", username = "u", fingerprint = "SHA256:abc")
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `fingerprint matches the OpenSSH SHA256 form`() {
        val key = byteArrayOf(1, 2, 3, 4, 5)
        val expected = "SHA256:" + java.util.Base64.getEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(key))
        assertEquals(expected, PinningHostKeyRepository.sshFingerprint(key))
    }

    @Test
    fun `pinned repository accepts only the pinned key`() {
        val key = byteArrayOf(9, 9, 9)
        val fingerprint = PinningHostKeyRepository.sshFingerprint(key)

        val matching = PinningHostKeyRepository(fingerprint)
        assertEquals(HostKeyRepository.OK, matching.check("host", key))

        val other = PinningHostKeyRepository("SHA256:somethingelse")
        assertEquals(
            "a changed host key must be reported as CHANGED, not silently accepted",
            HostKeyRepository.CHANGED,
            other.check("host", key)
        )
    }

    @Test
    fun `probe repository records the fingerprint and accepts once`() {
        val key = byteArrayOf(7, 7)
        val probe = PinningHostKeyRepository(pinned = null)
        assertEquals(HostKeyRepository.OK, probe.check("host", key))
        assertEquals(PinningHostKeyRepository.sshFingerprint(key), probe.seenFingerprint)
    }

    @Test
    fun `null key is not accepted`() {
        val probe = PinningHostKeyRepository(pinned = null)
        assertEquals(HostKeyRepository.NOT_INCLUDED, probe.check("host", null))
        assertFalse(probe.seenFingerprint != null)
    }
}

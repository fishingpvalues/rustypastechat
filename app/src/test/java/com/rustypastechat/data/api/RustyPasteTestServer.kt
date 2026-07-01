package com.rustypastechat.data.api

import kotlinx.coroutines.*
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

class RustyPasteTestServer(
    val port: Int = findFreePort(),
    val uploadDir: File = createTempDir("rustypaste_test_upload")
) : AutoCloseable {

    private var process: Process? = null
    val baseUrl: String get() = "http://127.0.0.1:$port"

    companion object {
        private var cachedPath: String? = null

        fun binaryPath(): String {
            if (cachedPath != null) return cachedPath!!
            val paths = listOf(
                "src/test/resources/rustypaste",
                "../app/src/test/resources/rustypaste",
                "app/src/test/resources/rustypaste"
            )
            for (p in paths) {
                val f = File(System.getProperty("user.dir"), p)
                if (f.exists() && f.canExecute()) {
                    cachedPath = f.absolutePath
                    return f.absolutePath
                }
            }
            // fallback: try to find via PATH
            val which = runCatching {
                ProcessBuilder("which", "rustypaste")
                    .redirectErrorStream(true)
                    .start()
                    .inputStream.bufferedReader().readLine()
            }.getOrNull()
            if (which != null) {
                cachedPath = which
                return which
            }
            throw IllegalStateException(
                "rustypaste binary not found. Build with: cd /tmp/rustypaste && cargo build --release"
            )
        }

        private fun findFreePort(): Int {
            ServerSocket(0).use { return it.localPort }
        }
    }

    fun start() {
        if (process != null && process!!.isAlive) return
        uploadDir.mkdirs()
        val bin = File(binaryPath())
        val pb = ProcessBuilder(
            bin.absolutePath,
        )
        pb.directory(uploadDir)
        pb.environment().apply {
            put("CONFIG", createTestConfig())
        }
        pb.redirectErrorStream(true)
        process = pb.start()

        // Read stdout to detect startup
        val reader = process!!.inputStream.bufferedReader()
        val started = runBlocking {
            withTimeout(15000L) {
                while (true) {
                    val line = reader.readLine() ?: throw Exception("Binary exited unexpectedly")
                    if (line.contains("starting") || line.contains("listening") || line.contains("actix") || line.contains("service")) {
                        return@withTimeout true
                    }
                    if (line.contains("error", ignoreCase = true) || line.contains("panic", ignoreCase = true)) {
                        throw Exception("Binary error: $line")
                    }
                }
                false
            }
        }
        // give it a moment
        Thread.sleep(500)
    }

    private fun createTestConfig(): String {
        val configFile = File(uploadDir, "config.toml")
        val config = """
            [server]
            address = "127.0.0.1:$port"
            max_content_length = "10MB"
            upload_path = "${uploadDir.absolutePath}/upload"
            timeout = "30s"
            expose_version = true
            expose_list = true
            handle_spaces = "replace"

            [paste]
            default_extension = "txt"
            duplicate_files = true
        """.trimIndent()
        configFile.writeText(config)
        return configFile.absolutePath
    }

    override fun close() {
        process?.let {
            it.destroyForcibly()
            it.waitFor(5, TimeUnit.SECONDS)
        }
        uploadDir.deleteRecursively()
    }
}

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
            val candidates = mutableListOf<String>()
            // Prefer a PATH-resolved binary first — it's guaranteed to match this
            // host's OS/arch, unlike a bundled test-resource binary that may have
            // been built for a different platform (e.g. Linux CI vs. macOS dev).
            runCatching {
                ProcessBuilder("which", "rustypaste")
                    .redirectErrorStream(true)
                    .start()
                    .inputStream.bufferedReader().readLine()
            }.getOrNull()?.let { candidates.add(it) }
            candidates.addAll(
                listOf(
                    "src/test/resources/rustypaste",
                    "../app/src/test/resources/rustypaste",
                    "app/src/test/resources/rustypaste"
                ).map { File(System.getProperty("user.dir"), it).absolutePath }
            )
            for (p in candidates) {
                val f = File(p)
                if (f.exists() && f.canExecute() && isExecutableForPlatform(p)) {
                    cachedPath = p
                    return p
                }
            }
            throw IllegalStateException(
                "No rustypaste binary compatible with ${System.getProperty("os.name")}/" +
                    "${System.getProperty("os.arch")} was found on PATH or in test resources. " +
                    "Install one with: cargo install rustypaste"
            )
        }

        /** Runs [path] briefly and confirms the OS could actually exec it (vs. e.g. a foreign-arch ELF/PE). */
        private fun isExecutableForPlatform(path: String): Boolean {
            return try {
                val proc = ProcessBuilder(path).redirectErrorStream(true).start()
                val output = StringBuilder()
                val reader = proc.inputStream.bufferedReader()
                val finished = proc.waitFor(3, TimeUnit.SECONDS)
                while (reader.ready()) {
                    output.append(reader.readLine() ?: break).append('\n')
                }
                if (!finished) proc.destroyForcibly()
                output.isNotEmpty()
            } catch (e: Exception) {
                false
            }
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

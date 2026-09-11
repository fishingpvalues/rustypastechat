package com.rustypastechat.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * The API contract, checked against a real rustypaste rather than the
 * in-process fake.
 *
 * [RustyPasteTestServer] is a fake this repository wrote, so agreeing with it
 * proves the client and the fake agree - not that either matches rustypaste.
 * Every claim in AGENTS.md's contract table was at some point true of the fake
 * and stale about the server: it asserted the potatostack instance "currently
 * accepts uploads without auth", which stopped being true and nobody noticed,
 * because nothing executed against the server.
 *
 * Skipped unless pointed at an instance, so the ordinary `make test` run stays
 * hermetic and offline:
 *
 *     RP_LIVE_URL=http://127.0.0.1:8788 \
 *     RP_LIVE_TOKEN=... RP_LIVE_DELETE_TOKEN=... ./gradlew testDebugUnitTest
 *
 * It writes one small paste per run and deletes it again when a delete token
 * is supplied.
 */
class LiveRustyPasteContractTest {

    private val baseUrl: String? = System.getenv("RP_LIVE_URL")?.trimEnd('/')
    private val token: String? = System.getenv("RP_LIVE_TOKEN")
    private val deleteToken: String? = System.getenv("RP_LIVE_DELETE_TOKEN")

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private fun apiWith(authToken: String?): RustyPasteApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                PasteAuthInterceptor(object : PasteAuthInterceptor.TokenProvider {
                    override fun getToken() = authToken
                })
            )
            .build()
        return Retrofit.Builder()
            .baseUrl("$baseUrl/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RustyPasteApi::class.java)
    }

    private fun part(text: String, name: String): MultipartBody.Part =
        MultipartBody.Part.createFormData(
            "file", name, text.toRequestBody("text/plain".toMediaType())
        )

    @Before
    fun requireLiveServer() {
        assumeFalse("RP_LIVE_URL not set - live contract test skipped", baseUrl.isNullOrBlank())
        assumeFalse("RP_LIVE_TOKEN not set - live contract test skipped", token.isNullOrBlank())
    }

    @Test
    fun anonymousCallerCannotListOrUpload() = runBlocking {
        val anon = apiWith(null)
        assertEquals("GET /list must require the auth token", 401, anon.listFiles().code())
        assertEquals(
            "POST / must require the auth token",
            401,
            anon.uploadFile(part("should not be accepted", "denied.txt")).code()
        )
    }

    @Test
    fun aWrongTokenIsRefusedExactlyLikeNoToken() = runBlocking {
        val wrong = apiWith("definitely-not-the-token")
        assertEquals(401, wrong.listFiles().code())
        assertEquals(401, wrong.uploadFile(part("nope", "denied.txt")).code())
    }

    @Test
    fun uploadListDownloadDelete() = runBlocking {
        val api = apiWith(token)
        val body = "rustypastechat live contract ${System.currentTimeMillis()}"

        val upload = api.uploadFile(part(body, "contract.txt"))
        assertEquals(200, upload.code())
        val url = upload.body()?.string()?.trim().orEmpty()
        assertTrue("upload must answer with a plain-text URL, got: $url", url.startsWith("http"))

        val filename = url.substringAfterLast('/')
        assertTrue("server assigns a random name, not ours", filename.isNotBlank())

        val fetched = api.getFile(filename)
        assertEquals(200, fetched.code())
        assertEquals("round-tripped content must be byte-identical", body, fetched.body()?.string())

        val listed = api.listFiles()
        assertEquals(200, listed.code())
        val entry = listed.body()?.firstOrNull { it.fileName == filename }
        assertNotNull("the upload must appear in /list", entry)
        assertTrue("/list must report a non-zero size", (entry?.fileSize ?: 0) > 0)

        // The property that decides this app's threat model: content is
        // readable by anyone who knows the name, with no token at all. The
        // random filename is the only secret protecting a message.
        val anonRead = apiWith(null).getFile(filename)
        assertEquals(
            "GET /{file} is public on rustypaste - if this ever returns 401 the " +
                "README's threat model section is out of date, not this test",
            200, anonRead.code()
        )

        if (deleteToken.isNullOrBlank()) return@runBlocking

        assertEquals(
            "DELETE must require the delete token",
            401, apiWith(token).deleteFile(filename).code()
        )
        assertEquals(200, apiWith(deleteToken).deleteFile(filename).code())
        assertEquals("deleted paste must be gone", 404, apiWith(token).getFile(filename).code())
    }

    @Test
    fun versionEndpointIsAbsentAndMustNotBeReliedOn() = runBlocking {
        // Declared in RustyPasteApi and never called. Pinned so a future
        // feature cannot quietly start depending on it.
        assertEquals(404, apiWith(token).getVersion().code())
    }
}

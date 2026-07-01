package com.rustypastechat.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.rustypastechat.data.model.LlmChatRequest
import com.rustypastechat.data.model.LlmDeltaResponse
import com.rustypastechat.data.model.LlmMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class LlmApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: OpenAiApi
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenAiApi::class.java)
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    @Test
    fun `chat completion returns response`() = runBlocking {
        val mockResponse = """
            {"choices":[{"message":{"role":"assistant","content":"Hello!"},"finish_reason":"stop"}]}
        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(mockResponse)
                .addHeader("Content-Type", "application/json")
        )

        val request = LlmChatRequest(
            model = "gpt-3.5-turbo",
            messages = listOf(LlmMessage("user", "Hi"))
        )
        val response = api.chatCompletion("Bearer test-key", request)
        assertTrue("Response should be successful", response.isSuccessful)
        val body = response.body()
        assertNotNull("Body should not be null", body)
        assertEquals("Hello!", body!!.choices.first().message?.content)
    }

    @Test
    fun `streaming response delivers chunks`() = runBlocking {
        val sseChunks = """
            data: {"choices":[{"delta":{"content":"Hello"},"index":0}]}
            
            data: {"choices":[{"delta":{"content":" world"},"index":0}]}
            
            data: [DONE]
            
        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(sseChunks)
                .addHeader("Content-Type", "text/event-stream")
        )

        val request = LlmChatRequest(
            model = "gpt-3.5-turbo",
            messages = listOf(LlmMessage("user", "Hi")),
            stream = true
        )
        val response = api.streamChatCompletion("Bearer test-key", request)
        assertTrue(response.isSuccessful)

        val body = response.body()?.string() ?: ""
        assertTrue("Stream should contain DONE", body.contains("[DONE]"))
        assertTrue("Stream should contain delta", body.contains("Hello"))
    }

    @Test
    fun `auth header is sent correctly`() = runBlocking {
        val mockResponse = """
            {"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(mockResponse))

        val request = LlmChatRequest(
            model = "gpt-3.5-turbo",
            messages = listOf(LlmMessage("user", "test"))
        )
        val response = api.chatCompletion("Bearer my-secret-key", request)
        assertTrue(response.isSuccessful)

        val recordedRequest = server.takeRequest()
        val authHeader = recordedRequest.getHeader("Authorization")
        assertEquals("Bearer my-secret-key", authHeader)
        assertEquals("application/json", recordedRequest.getHeader("Content-Type"))
    }

    @Test
    fun `streaming delta parse test`() {
        val data = """{"choices":[{"delta":{"content":"Hello world"},"index":0}]}"""
        val parsed = json.decodeFromString<LlmDeltaResponse>(data)
        assertEquals("Hello world", parsed.choices.firstOrNull()?.delta?.content)
    }

    @Test
    fun `delta with null content handled`() {
        val data = """{"choices":[{"delta":{"role":"assistant"},"index":0}]}"""
        val parsed = json.decodeFromString<LlmDeltaResponse>(data)
        assertNull(parsed.choices.firstOrNull()?.delta?.content)
        assertEquals("assistant", parsed.choices.firstOrNull()?.delta?.role)
    }
}

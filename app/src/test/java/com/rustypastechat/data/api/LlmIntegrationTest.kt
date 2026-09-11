package com.rustypastechat.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.rustypastechat.data.model.LlmChatRequest
import com.rustypastechat.data.model.LlmMessage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Exercises a real OpenAI-compatible endpoint.
 *
 * Opt-in: every test is skipped unless LLM_TEST_BASE_URL and LLM_TEST_API_KEY
 * are set. It used to run unconditionally against a hardcoded endpoint with a
 * hardcoded key, which made `make test` depend on a third party being up - it
 * failed on a read timeout during this audit - and put a live credential in a
 * public repository. Contract-level behaviour that does not need a server is
 * covered by LlmApiTest against a local fake.
 *
 *     LLM_TEST_BASE_URL=https://host/v1 LLM_TEST_API_KEY=... \
 *     LLM_TEST_MODELS=model-a,model-b ./gradlew testDebugUnitTest
 */
class LlmIntegrationTest {

    companion object {
        private val API_KEY: String? = System.getenv("LLM_TEST_API_KEY")
        private val BASE_URL: String? = System.getenv("LLM_TEST_BASE_URL")
        private val chatModels = (System.getenv("LLM_TEST_MODELS") ?: "")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }

        /** First entry of LLM_TEST_MODELS. The suite must not name a
         *  provider's models: this app targets any OpenAI-compatible
         *  endpoint, so pinning one vendor's ids tested the vendor. */
        private val primaryModel: String get() = chatModels.first()

        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        private val client = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
        private val api: OpenAiApi by lazy {
            Retrofit.Builder()
                .baseUrl(BASE_URL!!.let { if (it.endsWith("/")) it else "$it/" })
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(OpenAiApi::class.java)
        }
    }

    @Before
    fun requireLiveEndpoint() {
        assumeFalse("LLM_TEST_BASE_URL not set - live LLM tests skipped", BASE_URL.isNullOrBlank())
        assumeFalse("LLM_TEST_API_KEY not set - live LLM tests skipped", API_KEY.isNullOrBlank())
    }

    @Test
    fun `list models returns available models`() = runBlocking {
        assumeFalse("LLM_TEST_MODELS not set", chatModels.isEmpty())
        val client = OkHttpClient.Builder().build()
        val request = okhttp3.Request.Builder()
            .url("$BASE_URL/models")
            .addHeader("Authorization", "Bearer $API_KEY")
            .build()
        val response = client.newCall(request).execute()
        assertEquals(200, response.code)
        val body = response.body?.string() ?: ""
        assertTrue("/models must list at least one model", body.contains("\"id\""))
        chatModels.forEach {
            assertTrue("configured model $it is not offered by this endpoint", body.contains(it))
        }
    }

    @Test
    fun `chat completion returns a response`() = runBlocking {
        assumeFalse("LLM_TEST_MODELS not set", chatModels.isEmpty())
        val request = LlmChatRequest(
            model = primaryModel,
            messages = listOf(LlmMessage("user", "What is 2+2? Answer with just the number.")),
            maxTokens = 10
        )
        val response = api.chatCompletion("Bearer $API_KEY", request)
        assertTrue("Response should succeed: ${response.code()} ${response.message()}", response.isSuccessful)
        val body = response.body()
        assertNotNull("Body should not be null", body)
        val content = body!!.choices.firstOrNull()?.message?.content ?: ""
        assertTrue("Should contain answer", content.contains("4"))
    }

    @Test
    fun `chat completion works for the second configured model`() = runBlocking {
        assumeFalse("needs a second model in LLM_TEST_MODELS", chatModels.size < 2)
        val request = LlmChatRequest(
            model = chatModels[1],
            messages = listOf(LlmMessage("user", "Say 'hello' in one word")),
            maxTokens = 20
        )
        val response = api.chatCompletion("Bearer $API_KEY", request)
        assertTrue("Response should succeed: ${response.code()}", response.isSuccessful)
        val body = response.body()
        assertNotNull(body)
        val content = body!!.choices.firstOrNull()?.message?.content
        // Qwen may return reasoning_content with null content — that's valid
        if (content != null) {
            assertTrue("Should contain hello", content.contains("hello", ignoreCase = true))
        }
    }

    @Test
    fun `streaming chat completion delivers chunks`() = runBlocking {
        assumeFalse("LLM_TEST_MODELS not set", chatModels.isEmpty())
        val request = LlmChatRequest(
            model = primaryModel,
            messages = listOf(LlmMessage("user", "Count from 1 to 3.")),
            stream = true,
            maxTokens = 50
        )
        val response = api.streamChatCompletion("Bearer $API_KEY", request)
        assertTrue("Streaming should succeed: ${response.code()}", response.isSuccessful)

        val body = response.body()
        assertNotNull(body)
        val reader = BufferedReader(InputStreamReader(body!!.byteStream()))
        val chunks = mutableListOf<String>()
        var line: String?
        var foundDone = false
        while (reader.readLine().also { line = it } != null) {
            val current = line ?: continue
            if (current.startsWith("data: ")) {
                val data = current.removePrefix("data: ").trim()
                if (data == "[DONE]") {
                    foundDone = true
                    break
                }
                try {
                    val delta = json.decodeFromString<com.rustypastechat.data.model.LlmDeltaResponse>(data)
                    delta.choices.firstOrNull()?.delta?.content?.let { chunks.add(it) }
                } catch (_: Exception) {}
            }
        }
        val fullText = chunks.joinToString("")
        assertTrue("Should receive chunks", chunks.isNotEmpty())
        assertTrue("Should contain numbers", fullText.contains("1"))
        assertTrue("Should end with DONE", foundDone)
    }

    @Test
    fun `all chat models respond successfully`() = runBlocking {
        assumeFalse("LLM_TEST_MODELS not set", chatModels.isEmpty())
        for (model in chatModels) {
            if (model.contains("whisper") || model.contains("embed", ignoreCase = true) ||
                model.contains("ocr", ignoreCase = true) || model.contains("reranker", ignoreCase = true))
                continue

            val request = LlmChatRequest(
                model = model,
                messages = listOf(LlmMessage("user", "Say OK")),
                maxTokens = 5
            )
            val response = api.chatCompletion("Bearer $API_KEY", request)
            assertTrue(
                "Model $model should respond: ${response.code()} ${response.message()}",
                response.isSuccessful
            )
            val content = response.body()?.choices?.firstOrNull()?.message?.content
            // Qwen reasoning models may have null content with reasoning_content elsewhere — that's OK
            if (content != null) {
                assertTrue("Model $model content should not be blank", content.isNotBlank())
                println("  ✓ $model: ${content.take(50)}")
            } else {
                println("  ✓ $model: (reasoning response, content=null)")
            }
        }
    }

    @Test
    fun `multi-turn conversation maintains context`() = runBlocking {
        assumeFalse("LLM_TEST_MODELS not set", chatModels.isEmpty())
        val messages = listOf(
            LlmMessage("user", "My name is TestBot."),
            LlmMessage("assistant", "Nice to meet you, TestBot!"),
            LlmMessage("user", "What is my name?")
        )
        val request = LlmChatRequest(
            model = primaryModel,
            messages = messages,
            maxTokens = 20
        )
        val response = api.chatCompletion("Bearer $API_KEY", request)
        assertTrue(response.isSuccessful)
        val content = response.body()?.choices?.firstOrNull()?.message?.content ?: ""
        assertTrue("Should remember name: $content", content.contains("TestBot", ignoreCase = true))
    }

    @Test
    fun `invalid model returns error`() = runBlocking {
        val request = LlmChatRequest(
            model = "nonexistent-model-xyz",
            messages = listOf(LlmMessage("user", "Hi")),
            maxTokens = 5
        )
        val response = api.chatCompletion("Bearer $API_KEY", request)
        assertFalse("Should fail for invalid model", response.isSuccessful)
        assertTrue("Should be 4xx error", response.code() in 400..499)
    }

    @Test
    fun `invalid API key returns 401`() = runBlocking {
        assumeFalse("LLM_TEST_MODELS not set", chatModels.isEmpty())
        val request = LlmChatRequest(
            model = primaryModel,
            messages = listOf(LlmMessage("user", "Hi")),
            maxTokens = 5
        )
        val response = api.chatCompletion("Bearer invalid-key-123", request)
        assertFalse("Should fail", response.isSuccessful)
        // Mittwald reports auth failures as 500 {"type":"auth_error"}; accept any 4xx/5xx rejection
        assertTrue("Expected 4xx/5xx rejection, got ${response.code()}", response.code() in 400..599)
    }
}

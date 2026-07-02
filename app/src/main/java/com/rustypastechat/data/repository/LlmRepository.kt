package com.rustypastechat.data.repository

import com.rustypastechat.data.api.ApiClientFactory
import com.rustypastechat.data.local.PreferencesManager
import com.rustypastechat.data.model.LlmChatRequest
import com.rustypastechat.data.model.LlmMessage
import kotlinx.coroutines.flow.first
import okhttp3.ResponseBody
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LlmRepository @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val apiClientFactory: ApiClientFactory
) {
    private var api: com.rustypastechat.data.api.OpenAiApi? = null

    private suspend fun getSettings() = preferencesManager.settingsFlow.first()

    private suspend fun getApi(): com.rustypastechat.data.api.OpenAiApi {
        val settings = getSettings()
        if (api == null) {
            api = apiClientFactory.createOpenAiApi(settings.llmEndpoint)
        }
        return api!!
    }

    private suspend fun getAuthHeader(): String {
        val settings = getSettings()
        return "Bearer ${settings.llmApiKey}"
    }

    suspend fun sendMessage(
        messages: List<LlmMessage>,
        onDelta: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val settings = getSettings()
            val request = LlmChatRequest(
                model = settings.llmModel.ifBlank { "gpt-3.5-turbo" },
                messages = messages,
                stream = true
            )
            val response = getApi().streamChatCompletion(getAuthHeader(), request)
            if (response.isSuccessful) {
                val body = response.body()
                body?.let { processStream(it, onDelta, onComplete, onError) }
                    ?: onError("Empty response body")
            } else {
                onError("LLM error: ${response.code()} ${response.message()}")
            }
        } catch (e: Exception) {
            onError("LLM error: ${e.message}")
        }
    }

    private fun processStream(
        body: ResponseBody,
        onDelta: (String) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val reader = BufferedReader(InputStreamReader(body.byteStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line ?: continue
                if (currentLine.startsWith("data: ")) {
                    val data = currentLine.removePrefix("data: ").trim()
                    if (data == "[DONE]") {
                        onComplete()
                        return
                    }
                    try {
                        val json = kotlinx.serialization.json.Json {
                            ignoreUnknownKeys = true
                            coerceInputValues = true
                        }
                        val delta = json.decodeFromString<com.rustypastechat.data.model.LlmDeltaResponse>(data)
                        delta.choices.firstOrNull()?.delta?.content?.let { content ->
                            if (content.isNotEmpty()) {
                                onDelta(content)
                            }
                        }
                    } catch (_: Exception) {
                        // skip unparseable lines
                    }
                }
            }
            onComplete()
        } catch (e: Exception) {
            onError("Stream error: ${e.message}")
        }
    }
}

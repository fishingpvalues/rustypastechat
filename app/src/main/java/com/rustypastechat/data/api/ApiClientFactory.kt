package com.rustypastechat.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.rustypastechat.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiClientFactory @Inject constructor(
    private val tokenProvider: PasteAuthInterceptor.TokenProvider
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun createPasteHttpClient(tokenOverride: String? = null): OkHttpClient {
        // BODY logging writes the full request - Authorization header and paste
        // content included - into logcat, which any app with READ_LOGS or
        // anyone with adb can read. That is fine while debugging and is a
        // credential leak in a shipped build, so release logs nothing and the
        // header is redacted even in debug.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }
        // An override lets "Test Connection" authenticate with the token the
        // user has just typed but not yet saved. Without it the test used the
        // typed URL with the STORED token and answered 401, which reads as
        // "your token is wrong" for a token that is perfectly correct.
        val provider = tokenOverride?.let {
            object : PasteAuthInterceptor.TokenProvider {
                override fun getToken(): String? = it.ifBlank { null }
            }
        } ?: tokenProvider
        return OkHttpClient.Builder()
            .addInterceptor(PasteAuthInterceptor(provider))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * @param tokenOverride authenticate with this token instead of the stored
     * one. Used by the settings "Test Connection" button so it tests what is
     * on screen.
     */
    fun createPasteApi(baseUrl: String, tokenOverride: String? = null): RustyPasteApi {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "Paste server URL must start with http:// or https://"
        }
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(url)
            .client(createPasteHttpClient(tokenOverride))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RustyPasteApi::class.java)
    }

    private fun createLlmHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    fun createOpenAiApi(baseUrl: String): OpenAiApi {
        require(baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
            "LLM endpoint URL must start with http:// or https://"
        }
        val url = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(url)
            .client(createLlmHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenAiApi::class.java)
    }
}

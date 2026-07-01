package com.rustypastechat.data.api

import com.rustypastechat.data.model.LlmChatRequest
import com.rustypastechat.data.model.LlmChatResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming

interface OpenAiApi {
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") auth: String,
        @Body request: LlmChatRequest
    ): Response<LlmChatResponse>

    @Streaming
    @Headers("Content-Type: application/json")
    @POST("chat/completions")
    suspend fun streamChatCompletion(
        @Header("Authorization") auth: String,
        @Body request: LlmChatRequest
    ): Response<ResponseBody>
}

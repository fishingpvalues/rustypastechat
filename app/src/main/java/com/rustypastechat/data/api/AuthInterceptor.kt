package com.rustypastechat.data.api

import okhttp3.Interceptor
import okhttp3.Response

class PasteAuthInterceptor(
    private val tokenProvider: TokenProvider
) : Interceptor {

    interface TokenProvider {
        fun getToken(): String?
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.getToken()
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .addHeader("Authorization", token)
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}

package com.rustypastechat.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Sits in front of Coil's network fetches: serves already-downloaded media from
 * [EncryptedCache] (AES-encrypted on-device) instead of Coil's own plaintext disk cache,
 * and encrypts newly-fetched bytes before Coil ever writes them anywhere unencrypted.
 * Only installed when the user has "Encrypt Media Cache" turned on (see RustyPasteChatApp).
 */
class EncryptedMediaInterceptor(private val cache: EncryptedCache) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = request.url.pathSegments.lastOrNull()?.takeIf { it.isNotBlank() }
            ?: return chain.proceed(request)

        val cached = runBlocking(Dispatchers.IO) { cache.readMedia(key) }
        if (cached != null) {
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK (encrypted cache)")
                .body(cached.toResponseBody("application/octet-stream".toMediaTypeOrNull()))
                .build()
        }

        val response = chain.proceed(request)
        if (!response.isSuccessful) return response
        val bytes = response.body?.bytes() ?: return response
        runBlocking(Dispatchers.IO) { cache.writeMedia(key, bytes) }
        return response.newBuilder()
            .body(bytes.toResponseBody(response.body?.contentType()))
            .build()
    }
}

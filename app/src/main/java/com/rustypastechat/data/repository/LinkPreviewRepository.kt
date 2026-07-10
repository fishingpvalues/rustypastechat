package com.rustypastechat.data.repository

import com.rustypastechat.data.model.LinkPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unfurls a plain URL into an OpenGraph title/description/image card.
 *
 * Deliberately uses its own bare [OkHttpClient] rather than the paste-server API client:
 * link targets are arbitrary third-party sites, and the paste server's bearer auth token
 * must never be sent to them.
 */
@Singleton
class LinkPreviewRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    // ConcurrentHashMap rejects null values at runtime regardless of the declared nullable type —
    // box the (frequent) "fetched but no preview" case so a plain cache write never throws.
    private class CacheEntry(val preview: LinkPreview?)
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun fetch(url: String): LinkPreview? {
        cache[url]?.let { return it.preview }

        val result = withContext(Dispatchers.IO) {
            runCatching { fetchInternal(url) }.getOrNull()
        }
        cache[url] = CacheEntry(result)
        return result
    }

    private fun fetchInternal(url: String): LinkPreview? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android) RustyPasteChat/1.0")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val contentType = response.header("Content-Type") ?: ""
            if (!contentType.contains("text/html", ignoreCase = true)) return null

            val body = response.body ?: return null
            val html = readCapped(body.byteStream())

            val title = extractOgTag(html, "title") ?: extractTitleTag(html)
            val description = extractOgTag(html, "description") ?: extractMetaDescription(html)
            val image = extractOgTag(html, "image")?.let { resolveUrl(url, it) }
            val siteName = extractOgTag(html, "site_name")

            if (title == null && description == null && image == null) return null

            return LinkPreview(
                url = url,
                title = title?.let(::decodeHtmlEntities),
                description = description?.let(::decodeHtmlEntities),
                imageUrl = image,
                siteName = siteName?.let(::decodeHtmlEntities)
            )
        }
    }

    private fun readCapped(stream: java.io.InputStream, maxChars: Int = 200_000): String {
        val sb = StringBuilder()
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val buffer = CharArray(8192)
            while (sb.length < maxChars) {
                val n = reader.read(buffer)
                if (n <= 0) break
                sb.append(buffer, 0, n)
            }
        }
        return sb.toString()
    }

    private fun extractOgTag(html: String, property: String): String? {
        for (match in META_TAG_REGEX.findAll(html)) {
            val tag = match.value
            if (tag.contains("og:$property", ignoreCase = true)) {
                CONTENT_ATTR_REGEX.find(tag)?.groupValues?.get(1)?.let { return it }
            }
        }
        return null
    }

    private fun extractMetaDescription(html: String): String? {
        for (match in META_TAG_REGEX.findAll(html)) {
            val tag = match.value
            if (tag.contains("name=\"description\"", ignoreCase = true) || tag.contains("name='description'", ignoreCase = true)) {
                CONTENT_ATTR_REGEX.find(tag)?.groupValues?.get(1)?.let { return it }
            }
        }
        return null
    }

    private fun extractTitleTag(html: String): String? =
        TITLE_TAG_REGEX.find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun resolveUrl(pageUrl: String, maybeRelative: String): String {
        return runCatching { java.net.URI(pageUrl).resolve(maybeRelative).toString() }.getOrDefault(maybeRelative)
    }

    private fun decodeHtmlEntities(text: String): String = text
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    companion object {
        private val META_TAG_REGEX = Regex("<meta\\s+[^>]*>", RegexOption.IGNORE_CASE)
        private val CONTENT_ATTR_REGEX = Regex("content=[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
        private val TITLE_TAG_REGEX = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)
    }
}

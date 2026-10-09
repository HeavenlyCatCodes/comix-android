package com.comixreader.app.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Network rules for page and cover images:
 *  - the image CDN rejects requests that carry Referer/Origin,
 *  - a page may live under several mirror path prefixes, try them on failure,
 *  - obfuscated responses are decoded before they reach Coil.
 */
class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val request = original.newBuilder()
            .removeHeader("Referer")
            .removeHeader("Origin")
            .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            .build()

        var response = fetchWithRetry(chain, request)
        if (!response.isSuccessful) {
            for (alternate in mirrorUrls(request.url.toString())) {
                response.close()
                response = fetchWithRetry(chain, request.newBuilder().url(alternate).build())
                if (response.isSuccessful) break
            }
        }
        if (!response.isSuccessful) return response
        return decode(response)
    }

    private fun fetchWithRetry(chain: Interceptor.Chain, request: Request): Response {
        var response = chain.proceed(request)
        var attempt = 0
        while (!response.isSuccessful && response.code in RETRY_CODES && attempt < 3) {
            response.close()
            attempt++
            Thread.sleep(800L * attempt)
            response = chain.proceed(request)
        }
        return response
    }

    private fun mirrorUrls(url: String): List<String> {
        val match = MIRROR_SEGMENT.find(url) ?: return emptyList()
        return MIRROR_PREFIXES
            .map { url.replaceRange(match.range, it) }
            .filter { it != url }
    }

    private fun decode(response: Response): Response {
        val how = ImageUnscrambler.instructions { response.header(it) }
        if (!how.needsWork) return response
        val decoded = ImageUnscrambler.process(response.body!!.bytes(), how)
        val type = if (how.needsGrid) JPEG else (response.body?.contentType() ?: JPEG)
        return response.newBuilder()
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length")
            .header("Content-Type", type.toString())
            .body(decoded.toResponseBody(type))
            .build()
    }

    private companion object {
        val JPEG = "image/jpeg".toMediaType()
        val RETRY_CODES = setOf(404, 500, 502, 503, 504, 520, 522, 523)
        val MIRROR_SEGMENT = Regex("/(?:i5|hi|fcf|s?i+)/")
        val MIRROR_PREFIXES = listOf("/hi/", "/fcf/", "/i5/", "/si/", "/i/", "/sii/", "/ii/")
    }
}

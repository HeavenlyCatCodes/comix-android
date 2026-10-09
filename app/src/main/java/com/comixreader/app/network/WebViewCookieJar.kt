package com.comixreader.app.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Shares one cookie store between OkHttp and every WebView in the app, so a
 * Cloudflare clearance cookie earned in a WebView is sent by native requests.
 */
class WebViewCookieJar : CookieJar {
    private val manager: CookieManager get() = CookieManager.getInstance()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val urlString = url.toString()
        cookies.forEach { manager.setCookie(urlString, it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = manager.getCookie(url.toString()) ?: return emptyList()
        return header.split(';').mapNotNull { Cookie.parse(url, it.trim()) }
    }

    fun get(url: String, name: String): String? =
        manager.getCookie(url)
            ?.split(';')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')

    fun flush() = manager.flush()

    fun clear() {
        manager.removeAllCookies(null)
        manager.flush()
    }
}

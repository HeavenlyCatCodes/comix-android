package com.comixreader.app.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Hosts and URLs of the comix.to website. */
object Site {
    const val BASE_URL = "https://comix.to"
    val baseHttpUrl: HttpUrl = BASE_URL.toHttpUrl()

    private val siteDomains = listOf("comix.to", "comix.ws")

    /** True for the site itself and its CDN subdomains. */
    fun isSiteHost(host: String): Boolean =
        siteDomains.any { host == it || host.endsWith(".$it") }

    /** Hosts Cloudflare needs while it runs a challenge. */
    fun isChallengeHost(host: String): Boolean = host == "challenges.cloudflare.com"

    fun titleUrl(slug: String) = "$BASE_URL/title/$slug"
}

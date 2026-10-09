package com.comixreader.app.network

import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class HttpClients(context: Context) {
    val cookies = WebViewCookieJar()
    val solver = SilentChallengeSolver(context.applicationContext, cookies)

    /** Base client: shared cookies, a WebView-identical User-Agent, Cloudflare handling. */
    val base: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookies)
        .cache(Cache(File(context.cacheDir, "http"), 32L * 1024 * 1024))
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", UserAgent.value).build())
        }
        .addInterceptor(CloudflareInterceptor(solver))
        .build()

    /** Pages and covers. Coil keeps its own disk cache, so no HTTP cache here. */
    val images: OkHttpClient = base.newBuilder()
        .cache(null)
        .addInterceptor(ImageInterceptor())
        .build()
}

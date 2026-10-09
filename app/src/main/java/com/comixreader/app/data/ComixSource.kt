package com.comixreader.app.data

import android.content.Context
import com.comixreader.app.network.CloudflareChallengeException
import com.comixreader.app.network.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class HttpStatusException(val code: Int, url: String) : IOException("HTTP $code for $url")

/**
 * Talks to comix.to. Each call tries, in order:
 *  1. the JSON API, signed natively with harvested [CipherKeys] (fast),
 *  2. the data the server embeds in its HTML (`script#initial-data`),
 *  3. the page itself in a hidden WebView, which also harvests fresh keys.
 * Cloudflare challenges are never swallowed by a fallback: they surface as
 * [CloudflareChallengeException] so the UI can show the verification WebView.
 */
class ComixSource(
    context: Context,
    private val http: OkHttpClient,
    private val runner: PageRunner,
    private val settings: SettingsStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }
    private val keyPrefs = context.getSharedPreferences("api_keys", Context.MODE_PRIVATE)
    private val keyLock = Mutex()
    private val chapterCache = ConcurrentHashMap<String, List<Chapter>>()

    @Volatile
    private var cipher: ApiCipher? = loadSavedCipher()

    @Volatile
    private var lastHarvestFailure = 0L

    private val poster get() = settings.current.posterQuality

    // ---- public API ---------------------------------------------------------

    suspend fun browse(query: BrowseQuery, page: Int): Paged<MangaSummary> = io {
        val params = query.toParams(settings.current.maxContentRating, page)
        val list = attempt("browse") {
            signed("/api/v1/manga", params)?.let { decodeList<MangaDto>(it) }
        } ?: run {
            val url = browseUrl(params)
            val html = fetchHtml(url)
            listFromHtml(html) ?: listInWebView(url, html)
        }
        Paged(list.items.filter { it.hid.isNotEmpty() || it.url != null }.map { it.toSummary(poster) }, list.hasNext)
    }

    suspend fun details(slug: String): MangaDetails = io {
        val hid = slug.substringBefore('-')
        val html = fetchHtml(Site.titleUrl(slug))
        val fromHtml = initialQueries(html)?.let { queries ->
            queries.entries.firstOrNull { it.key.contains("\"detail\"") }?.value?.let { unwrap(it) }
        }
        val element = fromHtml
            ?: attempt("details") { signed("/api/v1/manga/$hid", emptyList())?.let { unwrap(it) } }
            ?: runPage(Site.titleUrl(slug), html, matcher = DETAIL_MATCHER)
        json.decodeFromJsonElement<MangaDto>(element).toDetails(poster)
    }

    suspend fun chapters(hid: String, slug: String, refresh: Boolean = false): List<Chapter> = io {
        if (!refresh) chapterCache[hid]?.let { return@io it }
        val dtos = attempt("chapters") { signedChapters(hid) } ?: chaptersInWebView(hid, slug)
        val list = dtos.distinctBy { it.id }.map { it.toChapter(slug) }.sortedByDescending { it.number }
        chapterCache[hid] = list
        list
    }

    fun cachedChapters(hid: String): List<Chapter>? = chapterCache[hid]

    suspend fun pages(chapter: Chapter): List<PageImage> = io {
        val pageUrl = "${Site.BASE_URL}/${chapter.path}"
        val element = attempt("pages") { signed("/api/v1/chapters/${chapter.id}", emptyList())?.let { unwrap(it) } }
            ?: run {
                val html = fetchHtml(pageUrl)
                initialQueries(html)?.values?.map { unwrap(it) }?.firstOrNull { hasPages(it) }
                    ?: runPage(pageUrl, html, matcher = PAGES_MATCHER).let { unwrap(it) }
            }
        buildPages(json.decodeFromJsonElement<ChapterPagesDto>(element).pages)
    }

    /** Drops harvested keys, e.g. from Settings when the site changed. */
    fun forgetKeys() {
        cipher = null
        keyPrefs.edit().clear().apply()
    }

    val hasKeys: Boolean get() = cipher != null

    // ---- strategies -----------------------------------------------------------

    private fun browseUrl(params: List<Pair<String, String>>): String =
        Site.baseHttpUrl.newBuilder().addPathSegment("browse")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build().toString()

    private fun listFromHtml(html: String): ListDto<MangaDto>? =
        initialQueries(html)?.values
            ?.mapNotNull { runCatching { decodeList<MangaDto>(it) }.getOrNull() }
            ?.firstOrNull { list -> list.items.isNotEmpty() && list.items.all { it.hid.isNotEmpty() } }

    private suspend fun listInWebView(url: String, html: String): ListDto<MangaDto> {
        val matcher = """
            function (v) {
              var r = v && v.result && Array.isArray(v.result.items) ? v.result : (Array.isArray(v.items) ? v : null);
              return r && r.items.length && r.items[0] && r.items[0].hid ? { result: r } : null;
            }
        """.trimIndent()
        return decodeList(runPage(url, html, matcher))
    }

    private fun signedChapters(hid: String): List<ChapterDto>? {
        if (cipher == null) return null
        val all = mutableListOf<ChapterDto>()
        for (page in 1..MAX_CHAPTER_PAGES) {
            val element = signed(
                "/api/v1/manga/$hid/chapters",
                listOf("limit" to "100", "order[number]" to "desc", "page" to page.toString()),
            ) ?: return null
            val list = decodeList<ChapterDto>(element)
            all += list.items
            if (!list.hasNext || list.items.isEmpty()) break
        }
        return all
    }

    private suspend fun chaptersInWebView(hid: String, slug: String): List<ChapterDto> {
        val pageUrl = Site.titleUrl(slug)
        val html = fetchHtml(pageUrl)
        // Short series often arrive complete in the server-rendered data.
        initialQueries(html)?.values
            ?.mapNotNull { runCatching { decodeList<ChapterDto>(it) }.getOrNull() }
            ?.firstOrNull { list -> !list.hasNext && list.items.any { it.number > 0 } }
            ?.let { return it.items }
        val doc = Jsoup.parse(html, pageUrl)
        // Boot only the API module, not the whole app: faster and quieter.
        val app = doc.selectFirst("script[type=module][src*=/dist/main-]")
        val appUrl = app?.absUrl("src").orEmpty()
        app?.remove()
        val watcher = """
            (function () {
              var cx = window.__cx;
              if (!cx || cx.started) return;
              cx.started = true;
              var hid = ${JSONObject.quote(hid)};
              var appUrl = ${JSONObject.quote(appUrl)};
              var load = new Function('u', 'return import(u)');
              (async function () {
                if (!appUrl) throw new Error('site script not found');
                var source = await (await fetch(appUrl)).text();
                var m = source.match(/from\s*["']\.\/(env-[^"']+\.js)["']/);
                if (!m) throw new Error('site API module not found');
                var mod = await load(new URL(m[1], appUrl).href);
                var api = Object.keys(mod).map(function (k) { return mod[k]; }).find(function (v) {
                  return v && typeof v === 'object' && typeof v.chapters === 'function';
                });
                if (!api) throw new Error('chapter API not found');
                var all = [];
                for (var page = 1; page <= $MAX_CHAPTER_PAGES; page++) {
                  var res = await api.chapters(hid, { page: page, limit: 100, order: { number: 'desc' } });
                  var body = res && res.result ? res.result : res;
                  var items = (body && body.items) || [];
                  if (!items.length) break;
                  all = all.concat(items);
                  var info = body.meta || body.pagination || {};
                  var last = info.lastPage || info.last_page || page;
                  if (!(info.hasNext || page < last)) break;
                }
                cx.post(JSON.stringify(all));
              })().catch(function (e) { cx.fail(e && e.message || e); });
            })();
        """.trimIndent()
        val result = runner.run(pageUrl, doc.outerHtml(), watcher, timeoutMs = 90_000)
        adoptKeys(result.keys)
        return json.decodeFromString(result.payload)
    }

    /** Runs a page and returns the first JSON value [matcher] accepts. */
    private suspend fun runPage(pageUrl: String, html: String, matcher: String): JsonElement {
        val result = runner.run(
            pageUrl = pageUrl,
            html = html,
            bootstrap = "window.__cx.match = $matcher;",
            watcher = "window.__cx && window.__cx.scanInitialData();",
        )
        adoptKeys(result.keys)
        return json.parseToJsonElement(result.payload)
    }

    // ---- signed API -----------------------------------------------------------

    /** Null when there are no keys yet. Throws when the call itself fails. */
    private fun signed(path: String, params: List<Pair<String, String>>): JsonElement? {
        val c = cipher ?: return null
        val entries = ApiCipher.canonical(params)
        val query = entries.joinToString("&") { (k, v) -> "$k=$v" }
        val url = Site.baseHttpUrl.newBuilder()
            .encodedPath(path)
            .apply { entries.forEach { (k, v) -> addQueryParameter(k, v) } }
            .addQueryParameter("_", c.sign(path, query))
            .build()
        val root = json.parseToJsonElement(get(url, "application/json, text/plain, */*"))
        val sealed = (root as? JsonObject)?.get("e") as? JsonPrimitive
        return if (sealed != null && sealed.isString) json.parseToJsonElement(c.decrypt(sealed.content)) else root
    }

    /**
     * Runs a signed call. A Cloudflare challenge propagates; any other failure
     * means the keys went stale, so they are dropped and null tells the caller
     * to use a fallback. Keys are harvested in the background of fallbacks.
     */
    private inline fun <T> attempt(what: String, block: () -> T?): T? {
        if (cipher == null) return null
        return try {
            block()
        } catch (e: CloudflareChallengeException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "signed $what failed, falling back", e)
            forgetKeys()
            null
        }
    }

    private suspend fun adoptKeys(keys: CipherKeys?) {
        if (keys == null || cipher != null) return
        keyLock.withLock {
            if (cipher != null) return
            val candidate = runCatching { ApiCipher(keys) }.getOrNull() ?: return
            cipher = candidate
            keyPrefs.edit().putString(KEY_MATERIAL, json.encodeToString(CipherKeys.serializer(), keys)).apply()
        }
    }

    private fun loadSavedCipher(): ApiCipher? = runCatching {
        val raw = keyPrefs.getString(KEY_MATERIAL, null) ?: return null
        ApiCipher(json.decodeFromString(CipherKeys.serializer(), raw))
    }.getOrNull()

    // ---- helpers ---------------------------------------------------------------

    private fun fetchHtml(url: String): String =
        get(url.toHttpUrl(), "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

    private fun get(url: HttpUrl, accept: String): String {
        val request = Request.Builder().url(url)
            .header("Accept", accept)
            .header("Referer", "${Site.BASE_URL}/")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw HttpStatusException(response.code, url.toString())
            return response.body?.string().orEmpty()
        }
    }

    private fun initialQueries(html: String): JsonObject? = runCatching {
        val raw = Jsoup.parse(html).selectFirst("script#initial-data")?.data() ?: return null
        (json.parseToJsonElement(raw) as JsonObject)["queries"] as? JsonObject
    }.getOrNull()

    private fun unwrap(element: JsonElement): JsonElement =
        ((element as? JsonObject)?.get("result") as? JsonObject) ?: element

    private fun hasPages(element: JsonElement): Boolean =
        ((element as? JsonObject)?.get("pages") as? JsonObject)?.get("items") is JsonArray

    private inline fun <reified T> decodeList(element: JsonElement): ListDto<T> =
        json.decodeFromJsonElement(unwrap(element))

    private fun buildPages(pages: PagesDto): List<PageImage> {
        val base = pages.baseUrl.trimEnd('/')
        return pages.items.filter { it.url.isNotBlank() }.mapIndexed { index, page ->
            val absolute = if (page.url.startsWith("http")) page.url else "$base/${page.url.trimStart('/')}"
            // Grid-scrambled pages only send their descramble headers when asked.
            val url = if (page.s == 1 && !absolute.contains("v3")) {
                absolute.toHttpUrl().newBuilder().addQueryParameter("v3", null).build().toString()
            } else {
                absolute
            }
            PageImage(index, url)
        }
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private companion object {
        const val TAG = "ComixSource"
        const val KEY_MATERIAL = "material"
        const val MAX_CHAPTER_PAGES = 100

        val DETAIL_MATCHER = """
            function (v) {
              var m = v && v.result && v.result.hid ? v.result : v;
              return m && m.hid && m.title && (m.synopsis !== undefined || m.genres || m.poster) ? m : null;
            }
        """.trimIndent()

        val PAGES_MATCHER = """
            function (v) {
              var r = v && v.result ? v.result : v;
              return r && r.pages && Array.isArray(r.pages.items) ? r : null;
            }
        """.trimIndent()
    }
}

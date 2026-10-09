# Comix Reader

A native Android reader for [comix.to](https://comix.to). It's built with Kotlin and Jetpack Compose, not a WebView wrapper. Lists, details, chapter lists and the reader are all native screens that use the site's data.

Unofficial. Not affiliated with comix.to.

## Features

- **Home**: Latest, Trending, Top rated and New feeds with infinite scroll.
- **Browse and search**: keyword search plus filters for sort, type, status, demographic, and include/exclude genres.
- **Series page**: cover, authors, score, synopsis, genres and the full chapter list. Duplicate uploads can be hidden (official first, then most voted). Long-press a chapter to mark it read.
- **Reader**:
  - vertical webtoon scroll, and paged right-to-left or left-to-right modes
  - mode picked automatically by series type, and can be changed per series
  - pinch and double-tap zoom, tap zones, page slider
  - previous/next chapter, preloading, immersive full screen, keep-screen-on
- **Library and history**: follows, continue reading, and page progress, all stored on the device.
- **Cloudflare handling**: see below.
- Opens `https://comix.to/title/...` links.

## Cloudflare captcha

comix.to sits behind Cloudflare. The app handles this in three steps:

1. **Shared session.** OkHttp and every WebView use the same cookie store (`CookieManager`) and the same User-Agent. A clearance cookie earned in a WebView works for native requests.
2. **Silent solve.** When a request gets a challenge page, the app first loads it in an invisible WebView for up to 20 seconds. Many challenges pass on their own there.
3. **Solve it yourself in a WebView.** If the silent attempt fails, the screen shows **"Verify in WebView"**. That opens the check in a full-screen WebView. When the new `cf_clearance` cookie appears, the screen closes by itself and the blocked screen reloads. If the screen doesn't close, tap **Done**.

You can also do this ahead of time: **Settings → Pass Cloudflare check**. Settings also has **Open comix.to in WebView**, **Clear cookies** and a custom **User agent** option.

## How data is loaded

The site signs its API requests and encrypts the responses. It also scrambles some page images. `ComixSource` tries three ways to get data, in order:

1. **Signed JSON API.** Requests are signed natively, and responses decrypted, with keys read from the site's own JavaScript at run time (`ApiCipher`, `PageRunner`). The keys are cached and refreshed when they stop working.
2. **Server-rendered data.** The `script#initial-data` block the site embeds in each page.
3. **Hidden WebView.** The page runs in an invisible WebView and the app captures the data the site's scripts produce. This step also refreshes the keys.

Obfuscated pages (XOR'd bytes and 5×5 tile shuffles, signalled by response headers) are decoded in an OkHttp interceptor before Coil displays them (`ImageUnscrambler`).

If the site changes and lists stop loading, use **Settings → Refresh API keys**.

## Project layout

```
app/src/main/java/com/comixreader/app/
  network/   OkHttp clients, shared cookies, Cloudflare detection/solver, image decoding
  data/      ComixSource, API cipher, hidden-WebView runner, DTOs, library and settings stores
  ui/        Compose screens: home, browse, details, reader, library/history, settings, web (captcha)
```

## Build

Requires JDK 17 and the Android SDK (API 35).

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

GitHub Actions (`.github/workflows/android.yml`) runs the tests and uploads debug and release APKs as the `comix-reader-apks` artifact on every push.

## Credits

The site's request signing and image obfuscation were understood partly by reading the open-source Comix extension in [keiyoushi/extensions-source](https://github.com/keiyoushi/extensions-source) (Apache-2.0). This app's code is a separate implementation.

package com.comixreader.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.comixreader.app.data.ComixSource
import com.comixreader.app.data.LibraryStore
import com.comixreader.app.data.PageRunner
import com.comixreader.app.data.SettingsStore
import com.comixreader.app.network.HttpClients
import com.comixreader.app.network.UserAgent

class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val http = HttpClients(app)
    val library = LibraryStore(app)
    val source = ComixSource(app, http.base, PageRunner(app), settings)
}

class ComixApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        UserAgent.init(this)
        container = AppContainer(this)
        UserAgent.override = container.settings.current.userAgent
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(container.http.images)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images"))
                .maxSizeBytes(512L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}

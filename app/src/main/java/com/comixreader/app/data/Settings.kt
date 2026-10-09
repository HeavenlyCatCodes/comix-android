package com.comixreader.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val readerMode: ReaderMode = ReaderMode.AUTO,
    val maxContentRating: String = "suggestive",
    val posterQuality: String = "large",
    val hideDuplicateChapters: Boolean = true,
    val keepScreenOn: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val userAgent: String = "",
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = state.asStateFlow()
    val current: AppSettings get() = state.value

    private fun read() = AppSettings(
        readerMode = enumOr(prefs.getString(K_READER, null), ReaderMode.AUTO),
        maxContentRating = prefs.getString(K_RATING, "suggestive") ?: "suggestive",
        posterQuality = prefs.getString(K_POSTER, "large") ?: "large",
        hideDuplicateChapters = prefs.getBoolean(K_DEDUPE, true),
        keepScreenOn = prefs.getBoolean(K_SCREEN, true),
        theme = enumOr(prefs.getString(K_THEME, null), ThemeMode.SYSTEM),
        userAgent = prefs.getString(K_UA, "") ?: "",
    )

    fun update(change: (AppSettings) -> AppSettings) {
        val next = change(state.value)
        prefs.edit()
            .putString(K_READER, next.readerMode.name)
            .putString(K_RATING, next.maxContentRating)
            .putString(K_POSTER, next.posterQuality)
            .putBoolean(K_DEDUPE, next.hideDuplicateChapters)
            .putBoolean(K_SCREEN, next.keepScreenOn)
            .putString(K_THEME, next.theme.name)
            .putString(K_UA, next.userAgent)
            .apply()
        state.value = next
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val K_READER = "reader_mode"
        const val K_RATING = "max_content_rating"
        const val K_POSTER = "poster_quality"
        const val K_DEDUPE = "hide_duplicate_chapters"
        const val K_SCREEN = "keep_screen_on"
        const val K_THEME = "theme"
        const val K_UA = "user_agent"
    }
}

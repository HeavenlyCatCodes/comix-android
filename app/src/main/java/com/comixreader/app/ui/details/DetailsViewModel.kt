package com.comixreader.app.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comixreader.app.AppContainer
import com.comixreader.app.data.Chapter
import com.comixreader.app.data.MangaDetails
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.comixreader.app.ui.common.Load

class DetailsViewModel(private val app: AppContainer, val slug: String) : ViewModel() {
    val hid: String = slug.substringBefore('-')

    private val _details = MutableStateFlow<Load<MangaDetails>>(Load.Loading)
    val details: StateFlow<Load<MangaDetails>> = _details.asStateFlow()

    private val _chapters = MutableStateFlow<Load<List<Chapter>>>(Load.Loading)
    val chapters: StateFlow<Load<List<Chapter>>> = _chapters.asStateFlow()

    val library = app.library.data
    val settings = app.settings.settings

    init {
        loadDetails()
        loadChapters(refresh = false)
    }

    fun loadDetails() {
        _details.value = Load.Loading
        viewModelScope.launch {
            _details.value = runCatching { app.source.details(slug) }
                .onSuccess { if (app.library.isSaved(hid)) app.library.refreshSaved(it.summary) }
                .fold({ Load.Ready(it) }, { Load.Failed(it) })
        }
    }

    fun loadChapters(refresh: Boolean = true) {
        _chapters.value = Load.Loading
        viewModelScope.launch {
            _chapters.value = runCatching { app.source.chapters(hid, slug, refresh) }
                .fold({ Load.Ready(it) }, { Load.Failed(it) })
        }
    }

    fun retryAll() {
        loadDetails()
        loadChapters()
    }

    fun toggleSaved() {
        val d = (_details.value as? Load.Ready)?.value ?: return
        app.library.toggleSaved(d.summary)
    }

    fun setRead(chapters: Collection<Chapter>, read: Boolean) =
        app.library.setFinished(hid, chapters.map { it.id }, read)

    fun setHideDuplicates(hide: Boolean) = app.settings.update { it.copy(hideDuplicateChapters = hide) }
}

/**
 * Several groups often upload the same chapter. Keep one per number:
 * official first, then the most voted, then the newest upload.
 */
fun List<Chapter>.withoutDuplicates(): List<Chapter> =
    groupBy { it.number }
        .values
        .map { same -> same.maxWith(compareBy<Chapter>({ it.official }, { it.votes }, { it.id })) }
        .sortedByDescending { it.number }

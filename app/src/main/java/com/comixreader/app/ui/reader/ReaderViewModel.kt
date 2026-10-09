package com.comixreader.app.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comixreader.app.AppContainer
import com.comixreader.app.data.Chapter
import com.comixreader.app.data.MangaSummary
import com.comixreader.app.data.PageImage
import com.comixreader.app.data.ReaderMode
import com.comixreader.app.data.resolve
import com.comixreader.app.ui.common.Load
import com.comixreader.app.ui.details.withoutDuplicates
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReaderState(
    val manga: MangaSummary? = null,
    val chapter: Chapter? = null,
    val pages: Load<List<PageImage>> = Load.Loading,
    /** Page to show when the chapter opens. */
    val startPage: Int = 0,
    val currentPage: Int = 0,
    val previous: Chapter? = null,
    val next: Chapter? = null,
    val mode: ReaderMode = ReaderMode.VERTICAL,
    val chosenMode: ReaderMode = ReaderMode.AUTO,
)

class ReaderViewModel(
    private val app: AppContainer,
    private val slug: String,
    chapterId: Long,
) : ViewModel() {
    private val hid = slug.substringBefore('-')
    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var chapters: List<Chapter> = emptyList()
    private var requestedId = chapterId

    init {
        open(chapterId)
    }

    fun open(chapterId: Long) {
        requestedId = chapterId
        loadJob?.cancel()
        _state.update { it.copy(pages = Load.Loading) }
        loadJob = viewModelScope.launch {
            val result = runCatching {
                val manga = _state.value.manga ?: runCatching { app.source.details(slug).summary }.getOrElse {
                    // History has enough to keep going without fresh details.
                    app.library.data.value.history.firstOrNull { it.manga.hid == hid }?.manga
                        ?: MangaSummary(hid, slug, "", null)
                }
                if (chapters.isEmpty()) {
                    chapters = app.source.cachedChapters(hid)?.takeIf { list -> list.any { it.id == chapterId } }
                        ?: app.source.chapters(hid, slug, refresh = true)
                }
                val chapter = chapters.firstOrNull { it.id == chapterId }
                    ?: error("Chapter not found. Refresh the chapter list and try again.")
                val (previous, next) = neighbours(chapter)
                val chosen = app.library.data.value.readerModes[hid] ?: app.settings.current.readerMode
                val saved = app.library.data.value.pagePositions[chapter.id] ?: 0
                _state.update {
                    it.copy(
                        manga = manga,
                        chapter = chapter,
                        previous = previous,
                        next = next,
                        chosenMode = chosen,
                        mode = chosen.resolve(manga.type),
                    )
                }
                val pages = app.source.pages(chapter)
                if (pages.isEmpty()) error("This chapter has no pages.")
                val start = if (saved >= pages.size - 1) 0 else saved
                _state.update { it.copy(pages = Load.Ready(pages), startPage = start, currentPage = start) }
            }
            result.onFailure { e -> _state.update { it.copy(pages = Load.Failed(e)) } }
        }
    }

    fun retry() {
        // A stale list may be why the chapter wasn't found.
        if (chapters.none { it.id == requestedId }) chapters = emptyList()
        open(requestedId)
    }

    /** Neighbours by chapter number, preferring the same group's upload. */
    private fun neighbours(current: Chapter): Pair<Chapter?, Chapter?> {
        fun pick(candidates: List<Chapter>) =
            candidates.firstOrNull { it.group == current.group }
                ?: candidates.withoutDuplicates().firstOrNull()
        val previousNumber = chapters.filter { it.number < current.number }.maxOfOrNull { it.number }
        val nextNumber = chapters.filter { it.number > current.number }.minOfOrNull { it.number }
        return pick(chapters.filter { it.number == previousNumber }) to pick(chapters.filter { it.number == nextNumber })
    }

    fun onPage(index: Int) {
        val s = _state.value
        val pages = (s.pages as? Load.Ready)?.value ?: return
        if (index == s.currentPage && index != 0) return
        _state.update { it.copy(currentPage = index) }
        val manga = s.manga ?: return
        val chapter = s.chapter ?: return
        app.library.recordProgress(manga, chapter, index, pages.size)
    }

    fun setMode(mode: ReaderMode) {
        app.library.setReaderMode(hid, mode)
        _state.update { it.copy(chosenMode = mode, mode = mode.resolve(it.manga?.type), startPage = it.currentPage) }
    }

    fun goNext() = _state.value.next?.let { open(it.id) }
    fun goPrevious() = _state.value.previous?.let { open(it.id) }
}

package com.comixreader.app.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comixreader.app.data.BrowseQuery
import com.comixreader.app.data.ComixSource
import com.comixreader.app.data.MangaSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MangaListState(
    val items: List<MangaSummary> = emptyList(),
    val firstLoad: Load<Unit> = Load.Loading,
    val loadingMore: Boolean = false,
    val moreError: Throwable? = null,
    val hasNext: Boolean = true,
)

/** Pages through browse results for a [BrowseQuery]. */
class MangaListViewModel(
    private val source: ComixSource,
    initial: BrowseQuery,
) : ViewModel() {
    private val _query = MutableStateFlow(initial)
    val query: StateFlow<BrowseQuery> = _query.asStateFlow()

    private val _state = MutableStateFlow(MangaListState())
    val state: StateFlow<MangaListState> = _state.asStateFlow()

    private var page = 0
    private var job: Job? = null

    init {
        reload()
    }

    fun setQuery(query: BrowseQuery) {
        if (query == _query.value) return
        _query.value = query
        reload()
    }

    fun reload() {
        job?.cancel()
        page = 0
        _state.value = MangaListState()
        loadNext()
    }

    fun loadMore() {
        val s = _state.value
        if (s.firstLoad !is Load.Ready || s.loadingMore || !s.hasNext) return
        loadNext()
    }

    private fun loadNext() {
        val query = _query.value
        val nextPage = page + 1
        _state.update { it.copy(loadingMore = page > 0, moreError = null) }
        job = viewModelScope.launch {
            runCatching { source.browse(query, nextPage) }
                .onSuccess { result ->
                    page = nextPage
                    _state.update { s ->
                        val merged = (s.items + result.items).distinctBy { it.hid }
                        s.copy(
                            items = merged,
                            firstLoad = Load.Ready(Unit),
                            loadingMore = false,
                            // A page that adds nothing new means we've reached the end.
                            hasNext = result.hasNext && merged.size > s.items.size,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { s ->
                        if (page == 0) s.copy(firstLoad = Load.Failed(error), loadingMore = false)
                        else s.copy(loadingMore = false, moreError = error)
                    }
                }
        }
    }
}

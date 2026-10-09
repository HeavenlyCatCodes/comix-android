@file:OptIn(ExperimentalMaterial3Api::class)

package com.comixreader.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comixreader.app.data.BrowseQuery
import com.comixreader.app.data.MangaSummary
import com.comixreader.app.ui.common.ErrorView
import com.comixreader.app.ui.common.Load
import com.comixreader.app.ui.common.LoadingView
import com.comixreader.app.ui.common.MangaGrid
import com.comixreader.app.ui.common.MangaListViewModel
import com.comixreader.app.ui.common.appContainer
import com.comixreader.app.ui.common.appViewModel

private data class Feed(val label: String, val query: BrowseQuery)

private val feeds = listOf(
    Feed("Latest", BrowseQuery(sort = "chapter_updated_at")),
    Feed("Trending", BrowseQuery(sort = "views_7d")),
    Feed("Top rated", BrowseQuery(sort = "score")),
    Feed("New", BrowseQuery(sort = "created_at")),
)

@Composable
fun HomeScreen(onOpen: (MangaSummary) -> Unit, onSearch: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val feed = feeds[tab]
    val vm = appViewModel(key = "home-${feed.label}") { MangaListViewModel(it.source, feed.query) }
    val state by vm.state.collectAsStateWithLifecycle()

    // Results depend on the content-rating setting; refresh when it changes.
    val settings by appContainer().settings.settings.collectAsStateWithLifecycle()
    var seenRating by rememberSaveable { mutableStateOf(settings.maxContentRating) }
    LaunchedEffect(settings.maxContentRating) {
        if (seenRating != settings.maxContentRating) {
            seenRating = settings.maxContentRating
            vm.reload()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comix") },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search") }
                    IconButton(onClick = vm::reload) { Icon(Icons.Default.Refresh, "Refresh") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                feeds.forEachIndexed { i, f ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(f.label) })
                }
            }
            when (val first = state.firstLoad) {
                Load.Loading -> LoadingView()
                is Load.Failed -> ErrorView(first.error, onRetry = vm::reload)
                is Load.Ready -> MangaGrid(
                    items = state.items,
                    onOpen = onOpen,
                    loadingMore = state.loadingMore,
                    moreError = state.moreError,
                    onLoadMore = vm::loadMore,
                )
            }
        }
    }
}

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.comixreader.app.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.comixreader.app.data.BrowseQuery
import com.comixreader.app.data.Catalog
import com.comixreader.app.data.MangaSummary
import com.comixreader.app.ui.common.EmptyView
import com.comixreader.app.ui.common.ErrorView
import com.comixreader.app.ui.common.Load
import com.comixreader.app.ui.common.LoadingView
import com.comixreader.app.ui.common.MangaGrid
import com.comixreader.app.ui.common.MangaListViewModel
import com.comixreader.app.ui.common.appViewModel

@Composable
fun BrowseScreen(onOpen: (MangaSummary) -> Unit, onBack: (() -> Unit)?, initialGenre: String? = null) {
    val vm = appViewModel(key = "browse-$initialGenre") {
        MangaListViewModel(
            it.source,
            BrowseQuery(sort = "views_30d", includeGenres = setOfNotNull(initialGenre)),
        )
    }
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    var text by rememberSaveable { mutableStateOf(query.keyword) }
    var showFilters by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    fun submit() {
        focus.clearFocus()
        val keyword = text.trim()
        vm.setQuery(query.copy(keyword = keyword, sort = if (keyword.isNotEmpty()) "relevance" else query.sort))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    }
                },
                title = {
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Search titles") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        trailingIcon = {
                            if (text.isNotEmpty()) {
                                IconButton(onClick = { text = ""; submit() }) { Icon(Icons.Default.Clear, "Clear") }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        BadgedBox(badge = {
                            if (query.filterCount > 0) Badge { Text(query.filterCount.toString()) }
                        }) { Icon(Icons.Default.FilterList, "Filters") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (val first = state.firstLoad) {
                Load.Loading -> LoadingView()
                is Load.Failed -> ErrorView(first.error, onRetry = vm::reload)
                is Load.Ready ->
                    if (state.items.isEmpty()) {
                        EmptyView("No results", "Try another title or loosen the filters.")
                    } else {
                        MangaGrid(
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

    if (showFilters) {
        FilterSheet(
            initial = query,
            onDismiss = { showFilters = false },
            onApply = {
                showFilters = false
                vm.setQuery(it.copy(keyword = text.trim()))
            },
        )
    }
}

@Composable
private fun FilterSheet(initial: BrowseQuery, onDismiss: () -> Unit, onApply: (BrowseQuery) -> Unit) {
    var q by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            Section("Sort by")
            ChipFlow {
                Catalog.sorts.forEach { option ->
                    FilterChip(
                        selected = q.sort == option.value,
                        onClick = { q = q.copy(sort = option.value) },
                        label = { Text(option.label) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ascending", Modifier.weight(1f))
                Switch(checked = q.ascending, onCheckedChange = { q = q.copy(ascending = it) })
            }

            Section("Type")
            MultiChips(Catalog.types, q.types) { q = q.copy(types = it) }
            Section("Status")
            MultiChips(Catalog.statuses, q.statuses) { q = q.copy(statuses = it) }
            Section("Demographic")
            MultiChips(Catalog.demographics, q.demographics) { q = q.copy(demographics = it) }

            Section("Genres")
            Text(
                "Tap to include, tap again to exclude, a third time to clear.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            ChipFlow {
                Catalog.genres.forEach { g ->
                    val included = g.value in q.includeGenres
                    val excluded = g.value in q.excludeGenres
                    val mark: (@Composable () -> Unit)? = when {
                        included -> { { Icon(Icons.Default.Check, null) } }
                        excluded -> { { Icon(Icons.Default.Block, null) } }
                        else -> null
                    }
                    FilterChip(
                        selected = included || excluded,
                        onClick = {
                            q = when {
                                included -> q.copy(includeGenres = q.includeGenres - g.value, excludeGenres = q.excludeGenres + g.value)
                                excluded -> q.copy(excludeGenres = q.excludeGenres - g.value)
                                else -> q.copy(includeGenres = q.includeGenres + g.value)
                            }
                        },
                        leadingIcon = mark,
                        label = { Text(g.label) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Match every included genre", Modifier.weight(1f))
                Switch(checked = q.matchAllGenres, onCheckedChange = { q = q.copy(matchAllGenres = it) })
            }

            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { q = BrowseQuery(keyword = q.keyword) },
                    modifier = Modifier.weight(1f),
                ) { Text("Reset") }
                Button(onClick = { onApply(q) }, modifier = Modifier.weight(1f)) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
private fun MultiChips(options: List<Catalog.Option>, selected: Set<String>, onChange: (Set<String>) -> Unit) {
    ChipFlow {
        options.forEach { option ->
            val on = option.value in selected
            FilterChip(
                selected = on,
                onClick = { onChange(if (on) selected - option.value else selected + option.value) },
                label = { Text(option.label) },
            )
        }
    }
}

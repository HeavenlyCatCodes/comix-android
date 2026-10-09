@file:OptIn(ExperimentalMaterial3Api::class)

package com.comixreader.app.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.comixreader.app.data.MangaSummary
import com.comixreader.app.data.ReadingRecord
import com.comixreader.app.ui.common.EmptyView
import com.comixreader.app.ui.common.MangaGrid
import com.comixreader.app.ui.common.appContainer

@Composable
fun LibraryScreen(onOpen: (MangaSummary) -> Unit) {
    val data by appContainer().library.data.collectAsStateWithLifecycle()
    Scaffold(topBar = { TopAppBar(title = { Text("Library") }) }) { padding ->
        if (data.saved.isEmpty()) {
            EmptyView(
                "Your library is empty",
                "Tap the bookmark on any series to keep it here.",
                Modifier.padding(padding),
            )
        } else {
            MangaGrid(
                items = data.saved.map { it.manga },
                onOpen = onOpen,
                modifier = Modifier.padding(padding),
                badge = { m ->
                    data.finished[m.hid]?.size?.takeIf { it > 0 }?.let { "$it read" }
                },
            )
        }
    }
}

@Composable
fun HistoryScreen(onResume: (ReadingRecord) -> Unit, onOpen: (MangaSummary) -> Unit) {
    val library = appContainer().library
    val data by library.data.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    if (data.history.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Default.DeleteSweep, "Clear history") }
                    }
                },
            )
        },
    ) { padding ->
        if (data.history.isEmpty()) {
            EmptyView("Nothing read yet", "Chapters you open show up here so you can pick up where you left off.", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(data.history, key = { it.manga.hid }) { record ->
                    HistoryRow(
                        record = record,
                        onResume = { onResume(record) },
                        onOpen = { onOpen(record.manga) },
                        onRemove = { library.removeHistory(record.manga.hid) },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("Library and read markers are kept.") },
            confirmButton = { TextButton(onClick = { library.clearHistory(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryRow(record: ReadingRecord, onResume: () -> Unit, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onResume).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = record.manga.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(56.dp)
                .height(80.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpen),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(record.manga.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(record.chapterTitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Page ${record.page + 1} of ${record.pageCount} · " +
                    DateUtils.getRelativeTimeSpanString(record.readAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (record.pageCount > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (record.page + 1f) / record.pageCount },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove") }
    }
}

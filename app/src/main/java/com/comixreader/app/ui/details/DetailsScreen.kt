@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.comixreader.app.ui.details

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.comixreader.app.data.Chapter
import com.comixreader.app.data.MangaDetails
import com.comixreader.app.network.Site
import com.comixreader.app.ui.common.ErrorView
import com.comixreader.app.ui.common.Load
import com.comixreader.app.ui.common.LoadingView
import com.comixreader.app.ui.common.appViewModel
import com.comixreader.app.ui.web.WebViewActivity

@Composable
fun DetailsScreen(
    slug: String,
    onBack: () -> Unit,
    onRead: (slug: String, chapterId: Long) -> Unit,
    onGenre: (String) -> Unit,
) {
    val vm = appViewModel(key = "details-$slug") { DetailsViewModel(it, slug) }
    val details by vm.details.collectAsStateWithLifecycle()
    val chaptersLoad by vm.chapters.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val saved = library.saved.any { it.manga.hid == vm.hid }
    val finished = library.finished[vm.hid].orEmpty()
    val allChapters = (chaptersLoad as? Load.Ready)?.value.orEmpty()
    val shown = (if (settings.hideDuplicateChapters) allChapters.withoutDuplicates() else allChapters)
        .let { if (newestFirst) it else it.reversed() }
    val lastRead = library.history.firstOrNull { it.manga.hid == vm.hid }
    val continueChapter = lastRead?.let { r -> allChapters.firstOrNull { it.id == r.chapterId } }
    val firstUnread = shown.sortedBy { it.number }.firstOrNull { it.id !in finished }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = vm::toggleSaved) {
                        Icon(if (saved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder, "Library")
                    }
                    IconButton(onClick = { context.startActivity(WebViewActivity.intent(context, Site.titleUrl(slug), false)) }) {
                        Icon(Icons.Default.Public, "Open in WebView")
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (settings.hideDuplicateChapters) "Show all uploads" else "Hide duplicate uploads") },
                            onClick = { menu = false; vm.setHideDuplicates(!settings.hideDuplicateChapters) },
                        )
                        DropdownMenuItem(
                            text = { Text("Mark all as read") },
                            onClick = { menu = false; vm.setRead(allChapters, true) },
                        )
                        DropdownMenuItem(
                            text = { Text("Mark all as unread") },
                            onClick = { menu = false; vm.setRead(allChapters, false) },
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh chapters") },
                            onClick = { menu = false; vm.loadChapters() },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (val d = details) {
            Load.Loading -> LoadingView(Modifier.padding(padding))
            is Load.Failed -> ErrorView(d.error, onRetry = vm::retryAll, modifier = Modifier.padding(padding))
            is Load.Ready -> LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                item { Header(d.value) }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val target = continueChapter ?: firstUnread ?: shown.lastOrNull()
                        Button(
                            onClick = { target?.let { onRead(slug, it.id) } },
                            enabled = target != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    continueChapter != null -> "Continue ch. ${continueChapter.numberLabel}"
                                    target != null -> "Read ch. ${target.numberLabel}"
                                    else -> "Read"
                                },
                            )
                        }
                        FilledTonalButton(onClick = vm::toggleSaved) {
                            Text(if (saved) "In library" else "Add to library")
                        }
                    }
                }
                item { Synopsis(d.value, onGenre) }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (chaptersLoad) {
                                is Load.Ready -> "${shown.size} chapters"
                                else -> "Chapters"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.loadChapters() }) { Icon(Icons.Default.Refresh, "Refresh chapters") }
                        IconButton(onClick = { newestFirst = !newestFirst }) { Icon(Icons.Default.SwapVert, "Reverse order") }
                    }
                }
                when (val c = chaptersLoad) {
                    Load.Loading -> item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Loading chapters. The first time can take a few seconds.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    is Load.Failed -> item {
                        Box(Modifier.height(380.dp)) { ErrorView(c.error, onRetry = { vm.loadChapters() }) }
                    }
                    is Load.Ready -> items(shown, key = { it.id }) { chapter ->
                        ChapterRow(
                            chapter = chapter,
                            read = chapter.id in finished,
                            current = chapter.id == lastRead?.chapterId,
                            onClick = { onRead(slug, chapter.id) },
                            onLongClick = { vm.setRead(listOf(chapter), chapter.id !in finished) },
                        )
                        HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
                item { Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp)) }
            }
        }
    }
}

@Composable
private fun Header(d: MangaDetails) {
    Box(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = d.summary.cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().blur(24.dp).alpha(0.35f),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.surface)),
            ),
        )
        Row(Modifier.padding(16.dp)) {
            AsyncImage(
                model = d.summary.cover,
                contentDescription = d.summary.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .height(172.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(d.summary.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                val people = (d.authors + d.artists).distinct()
                if (people.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(people.joinToString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    listOfNotNull(
                        d.summary.type?.replaceFirstChar { it.uppercase() },
                        d.summary.status?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
                        d.year?.toString(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (d.score > 0) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = Color(0xFFFFB300))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            buildString {
                                append("%.1f".format(d.score))
                                if (d.ratedCount > 0) append(" (${d.ratedCount})")
                                if (d.follows > 0) append(" · ${d.follows} follows")
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Synopsis(d: MangaDetails, onGenre: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp).animateContentSize()) {
        d.synopsis?.takeIf { it.isNotBlank() }?.let { text ->
            Text(
                text.trim(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Less" else "More") }
        }
        if (expanded && d.altTitles.isNotEmpty()) {
            Text("Also known as", style = MaterialTheme.typography.labelLarge)
            Text(d.altTitles.joinToString("\n"), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }
        val chips = d.genres + d.demographics + if (expanded) d.tags else emptyList()
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.distinct().forEach { name ->
                AssistChip(onClick = { onGenre(name) }, label = { Text(name) })
            }
        }
    }
}

@Composable
private fun ChapterRow(chapter: Chapter, read: Boolean, current: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val dim = if (read) 0.5f else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(dim),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                chapter.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                chapter.uploadedAt.takeIf { it > 0 }?.let {
                    DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.DAY_IN_MILLIS).toString()
                },
                chapter.group,
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        if (chapter.official) Icon(Icons.Default.Verified, "Official", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

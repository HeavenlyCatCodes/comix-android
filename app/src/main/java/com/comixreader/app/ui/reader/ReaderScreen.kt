@file:OptIn(ExperimentalMaterial3Api::class)

package com.comixreader.app.ui.reader

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChromeReaderMode
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import com.comixreader.app.data.PageImage
import com.comixreader.app.data.ReaderMode
import com.comixreader.app.ui.common.ErrorView
import com.comixreader.app.ui.common.Load
import com.comixreader.app.ui.common.appContainer
import com.comixreader.app.ui.common.appViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ReaderScreen(slug: String, chapterId: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "reader-$slug") { ReaderViewModel(it, slug, chapterId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by appContainer().settings.settings.collectAsStateWithLifecycle()
    var chrome by remember { mutableStateOf(true) }
    var jumpTo by remember { mutableStateOf<Int?>(null) }

    SystemBars(visible = chrome)
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val pages = state.pages) {
            Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color.White)
            }
            is Load.Failed -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
                ErrorView(pages.error, onRetry = vm::retry)
            }
            is Load.Ready -> {
                Prefetch(pages.value, state.currentPage)
                // New chapter or mode: rebuild the reader at the right page.
                val readerKey = "${state.chapter?.id}-${state.mode}"
                androidx.compose.runtime.key(readerKey) {
                    if (state.mode == ReaderMode.VERTICAL) {
                        VerticalReader(
                            pages = pages.value,
                            startPage = state.startPage,
                            jumpTo = jumpTo,
                            onJumped = { jumpTo = null },
                            onPage = vm::onPage,
                            onTap = { chrome = !chrome },
                            nextLabel = state.next?.title,
                            onNext = vm::goNext,
                        )
                    } else {
                        PagedReader(
                            pages = pages.value,
                            startPage = state.startPage,
                            rightToLeft = state.mode == ReaderMode.RIGHT_TO_LEFT,
                            jumpTo = jumpTo,
                            onJumped = { jumpTo = null },
                            onPage = vm::onPage,
                            onToggleChrome = { chrome = !chrome },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            TopBar(
                title = state.manga?.title.orEmpty(),
                subtitle = state.chapter?.title.orEmpty(),
                chosen = state.chosenMode,
                onMode = vm::setMode,
                onBack = onBack,
            )
        }
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            val count = (state.pages as? Load.Ready)?.value?.size ?: 0
            BottomBar(
                page = state.currentPage,
                count = count,
                hasPrevious = state.previous != null,
                hasNext = state.next != null,
                onPrevious = vm::goPrevious,
                onNext = vm::goNext,
                onSeek = { jumpTo = it },
            )
        }
    }
}

@Composable
private fun VerticalReader(
    pages: List<PageImage>,
    startPage: Int,
    jumpTo: Int?,
    onJumped: () -> Unit,
    onPage: (Int) -> Unit,
    onTap: () -> Unit,
    nextLabel: String?,
    onNext: () -> Unit,
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val zoom = remember { ZoomState(maxScale = 3f) }

    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { i ->
            if (i < pages.size) onPage(i)
        }
    }
    LaunchedEffect(jumpTo) {
        jumpTo?.let { list.scrollToItem(it); onJumped() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { zoom.toggle(this.size, it) },
                )
            },
    ) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().pinchZoom(zoom, panWithOneFinger = false)) {
            itemsIndexed(pages, key = { i, _ -> i }) { _, page ->
                PageView(page, fillWidth = true)
            }
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("End of chapter", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    if (nextLabel != null) {
                        Button(onClick = onNext) { Text("Next: $nextLabel", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else {
                        Text("You're caught up.", color = Color.LightGray)
                    }
                }
            }
        }
    }
}

@Composable
private fun PagedReader(
    pages: List<PageImage>,
    startPage: Int,
    rightToLeft: Boolean,
    jumpTo: Int?,
    onJumped: () -> Unit,
    onPage: (Int) -> Unit,
    onToggleChrome: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = startPage.coerceIn(0, pages.lastIndex)) { pages.size }
    val zooms = remember(pages) { List(pages.size) { ZoomState() } }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { i ->
            onPage(i)
            zooms.forEachIndexed { index, z -> if (index != i) z.reset() }
        }
    }
    LaunchedEffect(jumpTo) {
        jumpTo?.let { pager.scrollToPage(it); onJumped() }
    }

    var turnTo by remember { mutableIntStateOf(-1) }
    LaunchedEffect(turnTo) {
        if (turnTo in pages.indices) pager.animateScrollToPage(turnTo)
        turnTo = -1
    }

    HorizontalPager(
        state = pager,
        reverseLayout = rightToLeft,
        beyondViewportPageCount = 1,
        userScrollEnabled = !zooms[pager.currentPage].zoomed,
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        val zoom = zooms[index]
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(index, rightToLeft) {
                    detectTapGestures(
                        onDoubleTap = { zoom.toggle(size, it) },
                        onTap = { at ->
                            val third = size.width / 3f
                            // Tap the edges to turn; the side that means "forward" depends on direction.
                            val forward = if (rightToLeft) at.x < third else at.x > size.width - third
                            val backward = if (rightToLeft) at.x > size.width - third else at.x < third
                            when {
                                zoom.zoomed -> onToggleChrome()
                                forward -> turnTo = index + 1
                                backward -> turnTo = index - 1
                                else -> onToggleChrome()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().pinchZoom(zoom, panWithOneFinger = true), contentAlignment = Alignment.Center) {
                PageView(pages[index], fillWidth = false)
            }
        }
    }
}

@Composable
private fun PageView(page: PageImage, fillWidth: Boolean) {
    val context = LocalContext.current
    var attempt by remember(page.url) { mutableIntStateOf(0) }
    val request = remember(page.url, attempt) {
        ImageRequest.Builder(context)
            .data(page.url)
            .setParameter("attempt", attempt, memoryCacheKey = null)
            .build()
    }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = "Page ${page.index + 1}",
        contentScale = if (fillWidth) ContentScale.FillWidth else ContentScale.Fit,
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
        loading = {
            Box(Modifier.fillMaxWidth().height(480.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("${page.index + 1}", color = Color.Gray)
                }
            }
        },
        error = {
            Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Page ${page.index + 1} didn't load", color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { attempt++ }) { Text("Retry") }
                }
            }
        },
    )
}

/** Warms the image cache for the next few pages. */
@Composable
private fun Prefetch(pages: List<PageImage>, current: Int) {
    val context = LocalContext.current
    LaunchedEffect(pages, current) {
        val loader = context.imageLoader
        (current + 1..minOf(current + 4, pages.lastIndex)).forEach { i ->
            loader.enqueue(ImageRequest.Builder(context).data(pages[i].url).build())
        }
    }
}

@Composable
private fun TopBar(title: String, subtitle: String, chosen: ReaderMode, onMode: (ReaderMode) -> Unit, onBack: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        modifier = Modifier.background(Color(0xCC000000)).statusBarsPadding(),
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White,
            actionIconContentColor = Color.White,
        ),
        title = {
            Column {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.ChromeReaderMode, "Reading mode") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                listOf(
                    ReaderMode.AUTO to "Automatic (by series type)",
                    ReaderMode.VERTICAL to "Vertical scroll (webtoon)",
                    ReaderMode.RIGHT_TO_LEFT to "Paged, right to left (manga)",
                    ReaderMode.LEFT_TO_RIGHT to "Paged, left to right",
                ).forEach { (mode, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { RadioButton(selected = chosen == mode, onClick = null) },
                        onClick = { menu = false; onMode(mode) },
                    )
                }
            }
        },
    )
}

@Composable
private fun BottomBar(
    page: Int,
    count: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xCC000000))
            .navigationBarsPadding()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onPrevious, enabled = hasPrevious) {
            Icon(Icons.Default.SkipPrevious, "Previous chapter", tint = if (hasPrevious) Color.White else Color.Gray)
        }
        val shown = (dragging?.toInt() ?: page) + 1
        Text("$shown", color = Color.White, style = MaterialTheme.typography.labelMedium)
        if (count > 1) {
            Slider(
                value = dragging ?: page.toFloat(),
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { onSeek(it.toInt().coerceIn(0, count - 1)) }
                    dragging = null
                },
                valueRange = 0f..(count - 1).toFloat(),
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        Text("$count", color = Color.White, style = MaterialTheme.typography.labelMedium)
        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(Icons.Default.SkipNext, "Next chapter", tint = if (hasNext) Color.White else Color.Gray)
        }
    }
}

/** Immersive reading: system bars follow the reader chrome. */
@Composable
private fun SystemBars(visible: Boolean) {
    val view = LocalView.current
    DisposableEffect(visible) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.isAppearanceLightStatusBars = false
        if (visible) controller?.show(WindowInsetsCompat.Type.systemBars())
        else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

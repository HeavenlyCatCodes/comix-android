package com.comixreader.app.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class SavedManga(val manga: MangaSummary, val addedAt: Long)

@Serializable
data class ReadingRecord(
    val manga: MangaSummary,
    val chapterId: Long,
    val chapterTitle: String,
    val page: Int,
    val pageCount: Int,
    val readAt: Long,
)

@Serializable
data class LibraryData(
    val saved: List<SavedManga> = emptyList(),
    /** Most recent first, one record per series. */
    val history: List<ReadingRecord> = emptyList(),
    /** hid -> chapter ids read to the end. */
    val finished: Map<String, Set<Long>> = emptyMap(),
    /** chapter id -> last page index seen. */
    val pagePositions: Map<Long, Int> = emptyMap(),
    /** hid -> reader mode chosen for that series. */
    val readerModes: Map<String, ReaderMode> = emptyMap(),
)

/** Follows, history and progress, stored as one JSON file on the device. */
class LibraryStore(context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = AtomicFile(File(context.filesDir, "library.json"))
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()
    private val state = MutableStateFlow(load())
    val data: StateFlow<LibraryData> = state.asStateFlow()

    private fun load(): LibraryData = runCatching {
        json.decodeFromString<LibraryData>(file.readFully().decodeToString())
    }.getOrDefault(LibraryData())

    private fun change(block: (LibraryData) -> LibraryData) {
        state.update(block)
        val snapshot = state.value
        scope.launch {
            writeLock.withLock {
                val out = file.startWrite()
                try {
                    out.write(json.encodeToString(LibraryData.serializer(), snapshot).encodeToByteArray())
                    file.finishWrite(out)
                } catch (e: Exception) {
                    file.failWrite(out)
                }
            }
        }
    }

    fun isSaved(hid: String) = state.value.saved.any { it.manga.hid == hid }

    fun toggleSaved(manga: MangaSummary) = change { d ->
        if (d.saved.any { it.manga.hid == manga.hid }) {
            d.copy(saved = d.saved.filterNot { it.manga.hid == manga.hid })
        } else {
            d.copy(saved = listOf(SavedManga(manga, System.currentTimeMillis())) + d.saved)
        }
    }

    /** Keeps saved entries fresh (title or cover changes on the site). */
    fun refreshSaved(manga: MangaSummary) = change { d ->
        d.copy(saved = d.saved.map { if (it.manga.hid == manga.hid) it.copy(manga = manga) else it })
    }

    fun recordProgress(manga: MangaSummary, chapter: Chapter, page: Int, pageCount: Int) = change { d ->
        val record = ReadingRecord(manga, chapter.id, chapter.title, page, pageCount, System.currentTimeMillis())
        val reachedEnd = pageCount > 0 && page >= pageCount - 1
        val finishedForManga = d.finished[manga.hid].orEmpty()
        d.copy(
            history = listOf(record) + d.history.filterNot { it.manga.hid == manga.hid },
            pagePositions = d.pagePositions + (chapter.id to page),
            finished = if (reachedEnd) d.finished + (manga.hid to finishedForManga + chapter.id) else d.finished,
        )
    }

    fun setFinished(hid: String, chapterIds: Collection<Long>, finished: Boolean) = change { d ->
        val current = d.finished[hid].orEmpty()
        val next = if (finished) current + chapterIds else current - chapterIds.toSet()
        d.copy(finished = d.finished + (hid to next))
    }

    fun removeHistory(hid: String) = change { d -> d.copy(history = d.history.filterNot { it.manga.hid == hid }) }

    fun clearHistory() = change { it.copy(history = emptyList()) }

    fun setReaderMode(hid: String, mode: ReaderMode) = change { d ->
        d.copy(readerModes = if (mode == ReaderMode.AUTO) d.readerModes - hid else d.readerModes + (hid to mode))
    }
}

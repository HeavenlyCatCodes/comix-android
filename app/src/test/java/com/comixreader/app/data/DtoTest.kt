package com.comixreader.app.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun parsesMangaListWithOldFieldNames() {
        val body = """
            {"items":[{"hid":"ab12c","title":"Solo Story","url":"/title/ab12c-solo-story",
              "poster":{"medium":"https://cdn/m.jpg","large":null},"type":"manhwa",
              "alt_titles":["Solo"],"author":[{"title":"Writer"}],"synopsis":null,"extra":1}],
             "meta":{"page":1,"last_page":3}}
        """.trimIndent()
        val list = json.decodeFromString<ListDto<MangaDto>>(body)
        assertTrue(list.hasNext)
        val manga = list.items.single()
        val summary = manga.toSummary("large")
        assertEquals("ab12c-solo-story", summary.slug)
        assertEquals("https://cdn/m.jpg", summary.cover)
        assertEquals(listOf("Writer"), manga.toDetails("large").authors)
        assertEquals(listOf("Solo"), manga.altTitles)
    }

    @Test
    fun chapterPathFallsBackToSlug() {
        val chapter = json.decodeFromString<ChapterDto>("""{"id":991,"number":"12.5","name":null,"isOfficial":true}""")
            .toChapter("ab12c-solo-story")
        assertEquals("title/ab12c-solo-story/991-chapter-12.5", chapter.path)
        assertEquals("Official", chapter.group)
        assertEquals("Chapter 12.5", chapter.title)
    }

    @Test
    fun paginationHasNextFlagWins() {
        val list = json.decodeFromString<ListDto<ChapterDto>>("""{"items":[],"pagination":{"page":1,"lastPage":5,"hasNext":false}}""")
        assertFalse(list.hasNext)
    }

    @Test
    fun relativeDates() {
        assertEquals(0L, relativeToEpoch(""))
        assertTrue(relativeToEpoch("3 days ago") < System.currentTimeMillis())
        assertTrue(relativeToEpoch("2mos") > 0)
    }
}

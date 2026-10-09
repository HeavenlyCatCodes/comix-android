package com.comixreader.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseQueryTest {
    @Test
    fun relevanceWithoutKeywordFallsBackToLatest() {
        val params = BrowseQuery(sort = "relevance").toParams("", page = 1)
        assertTrue("order[chapter_updated_at]" to "desc" in params)
    }

    @Test
    fun contentRatingIncludesMilderRatings() {
        val params = BrowseQuery().toParams("erotica", page = 3)
        assertEquals(
            listOf("safe", "suggestive", "erotica"),
            params.filter { it.first == "content_rating[]" }.map { it.second },
        )
        assertTrue("page" to "3" in params)
    }

    @Test
    fun genreModeOnlyWithGenres() {
        assertTrue(BrowseQuery().toParams("", 1).none { it.first == "genres_mode" })
        val withGenre = BrowseQuery(includeGenres = setOf("6"), matchAllGenres = false).toParams("", 1)
        assertTrue("genres_mode" to "or" in withGenre)
        assertTrue("genres_in[]" to "6" in withGenre)
    }
}

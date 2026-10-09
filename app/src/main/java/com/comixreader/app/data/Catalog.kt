package com.comixreader.app.data

/** Search options the site understands, with its parameter values. */
object Catalog {
    data class Option(val label: String, val value: String)

    val sorts = listOf(
        Option("Latest update", "chapter_updated_at"),
        Option("Highest rated", "score"),
        Option("Most viewed (7 days)", "views_7d"),
        Option("Most viewed (30 days)", "views_30d"),
        Option("Most viewed (all time)", "views_total"),
        Option("Most followed", "follows_total"),
        Option("Recently added", "created_at"),
        Option("Title", "title"),
        Option("Year", "year"),
        Option("Relevance", "relevance"),
    )

    val types = listOf(
        Option("Manga", "manga"),
        Option("Manhwa", "manhwa"),
        Option("Manhua", "manhua"),
        Option("Other", "other"),
    )

    val statuses = listOf(
        Option("Releasing", "releasing"),
        Option("Finished", "finished"),
        Option("On hiatus", "on_hiatus"),
        Option("Discontinued", "discontinued"),
        Option("Not yet released", "not_yet_released"),
    )

    val demographics = listOf(
        Option("Shounen", "2"),
        Option("Shoujo", "1"),
        Option("Seinen", "4"),
        Option("Josei", "3"),
    )

    val genres = listOf(
        Option("Action", "6"), Option("Adventure", "7"), Option("Boys Love", "8"),
        Option("Comedy", "9"), Option("Crime", "10"), Option("Drama", "11"),
        Option("Fantasy", "12"), Option("Girls Love", "13"), Option("Harem", "40"),
        Option("Historical", "14"), Option("Horror", "15"), Option("Isekai", "16"),
        Option("Magical Girls", "17"), Option("Mecha", "18"), Option("Medical", "19"),
        Option("Mystery", "20"), Option("Philosophical", "21"), Option("Psychological", "22"),
        Option("Romance", "23"), Option("Sci-Fi", "24"), Option("Slice of Life", "25"),
        Option("Sports", "26"), Option("Superhero", "27"), Option("Thriller", "28"),
        Option("Tragedy", "29"), Option("Wuxia", "30"),
        Option("Ecchi", "87265"), Option("Mature", "87267"), Option("Adult", "87264"),
        Option("Smut", "87268"), Option("Hentai", "87266"),
        Option("Full Color", "93172"), Option("Long Strip", "93170"), Option("Web Comic", "93171"),
        Option("Oneshot", "93169"), Option("Adaptation", "93167"), Option("Anthology", "93165"),
        Option("4-Koma", "93164"), Option("Doujinshi", "93168"), Option("Award Winning", "93166"),
    )

    /** Ordered from mildest. A maximum includes everything before it. */
    val contentRatings = listOf(
        Option("Show everything", ""),
        Option("Safe only", "safe"),
        Option("Up to suggestive", "suggestive"),
        Option("Up to erotica", "erotica"),
        Option("Up to pornographic", "pornographic"),
    )

    fun ratingsUpTo(max: String): List<String> {
        val ladder = listOf("safe", "suggestive", "erotica", "pornographic")
        val i = ladder.indexOf(max)
        return if (i < 0) emptyList() else ladder.take(i + 1)
    }
}

data class BrowseQuery(
    val keyword: String = "",
    val sort: String = "chapter_updated_at",
    val ascending: Boolean = false,
    val types: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val demographics: Set<String> = emptySet(),
    val includeGenres: Set<String> = emptySet(),
    val excludeGenres: Set<String> = emptySet(),
    val matchAllGenres: Boolean = true,
) {
    val filterCount: Int
        get() = types.size + statuses.size + demographics.size + includeGenres.size + excludeGenres.size

    fun toParams(maxRating: String, page: Int, limit: Int = PAGE_SIZE): List<Pair<String, String>> = buildList {
        val order = if (sort == "relevance" && keyword.isBlank()) "chapter_updated_at" else sort
        add("order[$order]" to if (ascending) "asc" else "desc")
        if (keyword.isNotBlank()) add("keyword" to keyword.trim())
        types.sorted().forEach { add("types[]" to it) }
        statuses.sorted().forEach { add("statuses[]" to it) }
        demographics.sorted().forEach { add("demographics[]" to it) }
        includeGenres.sorted().forEach { add("genres_in[]" to it) }
        excludeGenres.sorted().forEach { add("genres_ex[]" to it) }
        if (includeGenres.isNotEmpty() || excludeGenres.isNotEmpty()) {
            add("genres_mode" to if (matchAllGenres) "and" else "or")
        }
        Catalog.ratingsUpTo(maxRating).forEach { add("content_rating[]" to it) }
        add("page" to page.toString())
        add("limit" to limit.toString())
    }

    companion object {
        const val PAGE_SIZE = 28
    }
}

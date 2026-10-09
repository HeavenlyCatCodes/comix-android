@file:OptIn(ExperimentalSerializationApi::class)

package com.comixreader.app.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.util.Calendar

// Wire formats of comix.to. Field names have changed over time, so older
// spellings are accepted through @JsonNames and every field has a default.

@Serializable
class TermDto(val title: String = "")

@Serializable
class PosterDto(
    val small: String? = null,
    val medium: String? = null,
    val large: String? = null,
) {
    fun pick(quality: String) = when (quality) {
        "small" -> small ?: medium ?: large
        "medium" -> medium ?: large ?: small
        else -> large ?: medium ?: small
    }
}

@Serializable
class MangaDto(
    val hid: String = "",
    val title: String = "",
    @JsonNames("alt_titles") val altTitles: List<String> = emptyList(),
    val synopsis: String? = null,
    val type: String? = null,
    val poster: PosterDto? = null,
    val status: String? = null,
    val contentRating: String? = null,
    @JsonNames("author") val authors: List<TermDto> = emptyList(),
    @JsonNames("artist") val artists: List<TermDto> = emptyList(),
    @JsonNames("genre") val genres: List<TermDto> = emptyList(),
    @JsonNames("theme") val tags: List<TermDto> = emptyList(),
    @JsonNames("demographic") val demographics: List<TermDto> = emptyList(),
    val formats: List<TermDto> = emptyList(),
    val ratedAvg: Double = 0.0,
    val ratedCount: Long = 0,
    val followsTotal: Long = 0,
    val rank: Int = 0,
    val year: Int? = null,
    val originalLanguage: String? = null,
    val links: Map<String, String?> = emptyMap(),
    val url: String? = null,
) {
    private val slug: String
        get() = url?.substringAfter("/title/", "")?.trim('/')?.substringBefore('/')
            ?.takeIf { it.isNotEmpty() } ?: hid

    fun toSummary(posterQuality: String) = MangaSummary(
        hid = hid.ifEmpty { slug.substringBefore('-') },
        slug = slug,
        title = title,
        cover = poster?.pick(posterQuality),
        type = type,
        status = status,
    )

    fun toDetails(posterQuality: String) = MangaDetails(
        summary = toSummary(posterQuality),
        altTitles = altTitles,
        synopsis = synopsis,
        authors = authors.map { it.title }.filter { it.isNotBlank() },
        artists = artists.map { it.title }.filter { it.isNotBlank() },
        genres = (genres + formats).map { it.title }.filter { it.isNotBlank() }.distinct(),
        tags = tags.map { it.title }.filter { it.isNotBlank() },
        demographics = demographics.map { it.title }.filter { it.isNotBlank() },
        contentRating = contentRating,
        score = ratedAvg,
        ratedCount = ratedCount,
        follows = followsTotal,
        rank = rank,
        year = year?.takeIf { it > 0 },
        language = originalLanguage,
        links = links.mapNotNull { (k, v) -> v?.takeIf { it.startsWith("http") }?.let { k to it } }.toMap(),
    )
}

@Serializable
class PageInfoDto(
    val page: Int = 1,
    @JsonNames("last_page") val lastPage: Int = 1,
    val hasNext: Boolean? = null,
) {
    val more: Boolean get() = hasNext ?: (page < lastPage)
}

@Serializable
class ListDto<T>(
    val items: List<T> = emptyList(),
    val meta: PageInfoDto? = null,
    val pagination: PageInfoDto? = null,
) {
    val hasNext: Boolean get() = (meta ?: pagination)?.more ?: false
}

@Serializable
class GroupDto(val id: Long? = null, val name: String = "")

@Serializable
class ChapterDto(
    val id: Long,
    val url: String = "",
    val number: Double = 0.0,
    val name: String? = null,
    val votes: Int = 0,
    val createdAtFormatted: String? = null,
    val group: GroupDto? = null,
    val isOfficial: Boolean = false,
) {
    fun toChapter(slug: String): Chapter {
        val fromUrl = url.indexOf("title/").takeIf { it >= 0 }?.let { url.substring(it) }
        val numberText = if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()
        return Chapter(
            id = id,
            number = number,
            name = name.orEmpty().trim(),
            group = group?.name?.takeIf { it.isNotBlank() } ?: if (isOfficial) "Official" else null,
            official = isOfficial,
            votes = votes,
            uploadedAt = relativeToEpoch(createdAtFormatted),
            path = fromUrl ?: "title/$slug/$id-chapter-$numberText",
        )
    }
}

@Serializable
class PageDto(val url: String = "", val s: Int = 0)

@Serializable
class PagesDto(val baseUrl: String = "", val items: List<PageDto> = emptyList())

@Serializable
class ChapterPagesDto(val pages: PagesDto)

/** "3 days ago", "5h", "2 mos" -> epoch millis. */
internal fun relativeToEpoch(text: String?): Long {
    val match = RELATIVE.find(text.orEmpty().trim().lowercase().removeSuffix(" ago")) ?: return 0L
    val amount = match.groupValues[1].toIntOrNull() ?: return 0L
    val field = when (match.groupValues[2]) {
        "s", "sec", "secs", "second", "seconds" -> Calendar.SECOND
        "m", "min", "mins", "minute", "minutes" -> Calendar.MINUTE
        "h", "hr", "hrs", "hour", "hours" -> Calendar.HOUR_OF_DAY
        "d", "day", "days" -> Calendar.DAY_OF_YEAR
        "w", "week", "weeks" -> Calendar.WEEK_OF_YEAR
        "mo", "mos", "month", "months" -> Calendar.MONTH
        else -> Calendar.YEAR
    }
    return Calendar.getInstance().apply { add(field, -amount) }.timeInMillis
}

private val RELATIVE =
    Regex("""^(\d+)\s*(seconds?|secs?|s|minutes?|mins?|m|hours?|hrs?|h|days?|d|weeks?|w|months?|mos?|years?|yrs?|y)$""")

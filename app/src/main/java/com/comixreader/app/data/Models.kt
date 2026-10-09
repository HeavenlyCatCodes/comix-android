package com.comixreader.app.data

import kotlinx.serialization.Serializable

@Serializable
data class MangaSummary(
    /** Short id, e.g. `x7k2p`. */
    val hid: String,
    /** Path segment after `/title/`, e.g. `x7k2p-some-series`. */
    val slug: String,
    val title: String,
    val cover: String?,
    val type: String? = null,
    val status: String? = null,
)

data class MangaDetails(
    val summary: MangaSummary,
    val altTitles: List<String>,
    val synopsis: String?,
    val authors: List<String>,
    val artists: List<String>,
    val genres: List<String>,
    val tags: List<String>,
    val demographics: List<String>,
    val contentRating: String?,
    val score: Double,
    val ratedCount: Long,
    val follows: Long,
    val rank: Int,
    val year: Int?,
    val language: String?,
    val links: Map<String, String>,
)

data class Chapter(
    val id: Long,
    val number: Double,
    val name: String,
    val group: String?,
    val official: Boolean,
    val votes: Int,
    /** Epoch millis, 0 when unknown. */
    val uploadedAt: Long,
    /** Site path without leading slash, `title/<slug>/<id>-chapter-<n>`. */
    val path: String,
) {
    val numberLabel: String
        get() = if (number % 1.0 == 0.0) number.toLong().toString() else number.toString()

    val title: String
        get() = buildString {
            append("Chapter ").append(numberLabel)
            if (name.isNotBlank()) append(": ").append(name)
        }
}

data class PageImage(val index: Int, val url: String)

data class Paged<T>(val items: List<T>, val hasNext: Boolean)

enum class ReaderMode { AUTO, VERTICAL, LEFT_TO_RIGHT, RIGHT_TO_LEFT }

fun ReaderMode.resolve(type: String?): ReaderMode = when (this) {
    ReaderMode.AUTO -> when (type) {
        "manga" -> ReaderMode.RIGHT_TO_LEFT
        "manhwa", "manhua" -> ReaderMode.VERTICAL
        else -> ReaderMode.LEFT_TO_RIGHT
    }
    else -> this
}

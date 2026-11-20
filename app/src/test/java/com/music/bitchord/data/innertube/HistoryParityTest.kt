package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * Parity between the two history parsers.
 *
 * `parseHistoryPage` (section-aware, keeps repeats) and `collectSongsDeep`
 * (deep walk, distinct) must agree on which tracks appear and in what order
 * once both are collapsed to one row per videoId. Home's "Recently played"
 * shelf is built from the deep walk today, so this is the gate before any
 * consumer moves to the section parser.
 *
 * All responses here are synthetic. No real account data is used.
 */
class HistoryParityTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun row(videoId: String, title: String, artist: String?): String {
        val artistColumn = if (artist == null) "" else """
            ,{"musicResponsiveListItemFlexColumnRenderer": {
                "text": {"runs": [{"text": "$artist", "navigationEndpoint": {"browseEndpoint": {"browseId": "UC_$artist"}}}]}
            }}
        """.trimIndent()
        return """
            {"musicResponsiveListItemRenderer": {
                "playlistItemData": {"videoId": "$videoId"},
                "flexColumns": [
                    {"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "$title"}]}}}
                    $artistColumn
                ]
            }}
        """.trimIndent()
    }

    private fun shelf(title: String, rows: List<String>): String =
        """{"musicShelfRenderer": {"title": {"runs": [{"text": "$title"}]}, "contents": [${rows.joinToString(",")}]}}"""

    private fun page(vararg shelves: String, continuation: String? = null): String {
        val token = continuation?.let { ""","continuations": [{"nextContinuationData": {"continuation": "$it"}}]""" } ?: ""
        return """{"contents": {"sectionListRenderer": {"contents": [${shelves.joinToString(",")}]}}$token}"""
    }

    private fun parse(raw: String) = json.parseToJsonElement(raw)

    @Test
    fun deepWalkAndSectionParserAgreeOnOrderOfDistinctTracks() {
        val root = parse(
            page(
                shelf(
                    "Today",
                    listOf(
                        row("aaa", "Alpha", "Artist A"),
                        row("bbb", "Bravo", "Artist B"),
                        row("aaa", "Alpha", "Artist A"), // repeat, same day
                    ),
                ),
                shelf(
                    "Yesterday",
                    listOf(
                        row("ccc", "Charlie", "Artist C"),
                        row("bbb", "Bravo", "Artist B"), // repeat, earlier day
                    ),
                ),
                continuation = "next-page",
            ),
        )

        val sectionIds = InnertubeParser.parseHistoryPage(root).songs
            .distinctBy { it.videoId }.take(20).map { it.videoId }
        val deepIds = InnertubeParser.collectSongsDeep(root).take(20).map { it.videoId }

        assertEquals(deepIds, sectionIds)
        assertEquals(listOf("aaa", "bbb", "ccc"), sectionIds)
    }

    @Test
    fun repeatsStayVisibleOnlyInTheSectionParser() {
        val root = parse(
            page(
                shelf("Today", listOf(row("aaa", "Alpha", "Artist A"), row("aaa", "Alpha", "Artist A"))),
            ),
        )

        val sectionRows = InnertubeParser.parseHistoryPage(root).songs.map { it.videoId }
        val deepRows = InnertubeParser.collectSongsDeep(root).map { it.videoId }

        assertEquals(listOf("aaa", "aaa"), sectionRows)
        assertEquals(listOf("aaa"), deepRows)
    }

    @Test
    fun missingArtistColumnDoesNotDropTheRow() {
        val root = parse(
            page(shelf("Today", listOf(row("ddd", "No Artist Track", artist = null)))),
        )

        val sectionIds = InnertubeParser.parseHistoryPage(root).songs.map { it.videoId }
        val deepIds = InnertubeParser.collectSongsDeep(root).map { it.videoId }

        assertEquals(listOf("ddd"), sectionIds)
        assertEquals(listOf("ddd"), deepIds)
    }

    @Test
    fun emptyResponseGivesEmptyResultFromBothParsers() {
        val root = parse(page())

        assertEquals(emptyList(), InnertubeParser.parseHistoryPage(root).songs)
        assertEquals(emptyList(), InnertubeParser.collectSongsDeep(root))
    }
}

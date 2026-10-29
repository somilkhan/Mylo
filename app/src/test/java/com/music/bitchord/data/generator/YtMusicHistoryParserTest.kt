package com.music.bitchord.data.generator

import com.music.bitchord.data.innertube.InnertubeParser
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class YtMusicHistoryParserTest {
    private val zone = ZoneId.of("UTC")
    private val now = LocalDate.of(2026, 10, 29)
        .atTime(9, 0)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun historyRowsInTodayAndYesterdayBucketsGetApproximateDates() {
        val json = Json.parseToJsonElement(
            """
            {
              "contents": [
                {
                  "musicShelfRenderer": {
                    "title": {"runs": [{"text": "Today"}]},
                    "contents": [
                      ${row("same", "Track A", "Artist A")},
                      ${row("same", "Track A", "Artist A")}
                    ]
                  }
                },
                {
                  "musicShelfRenderer": {
                    "title": {"runs": [{"text": "Yesterday"}]},
                    "contents": [${row("older", "Track B", "Artist B")}]
                  }
                },
                {
                  "musicShelfRenderer": {
                    "title": {"runs": [{"text": "Older"}]},
                    "contents": [${row("unknown-date", "Track C", "Artist C")}]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        val entries = InnertubeParser.collectHistoryEntries(json, now, zone)

        assertEquals(listOf("same", "same", "older"), entries.map { it.song.videoId })
        assertEquals(now, entries[0].playedAtMillis)
        assertEquals(
            LocalDate.of(2026, 10, 28).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            entries[2].playedAtMillis,
        )
        assertFalse(entries.any { it.song.videoId == "unknown-date" })
        assertFalse(entries.any { it.exact })
    }

    private fun row(id: String, title: String, artist: String): String =
        """
        {
          "musicResponsiveListItemRenderer": {
            "playlistItemData": {"videoId": "$id"},
            "flexColumns": [
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": {"runs": [{"text": "$title"}]}
                }
              },
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": {"runs": [{"text": "$artist"}, {"text": " • "}, {"text": "Song"}]}
                }
              }
            ]
          }
        }
        """.trimIndent()
}

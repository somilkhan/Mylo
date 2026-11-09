package com.music.bitchord.data.innertube

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryPageParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun preservesSectionsAndRepeatedPlays() {
        val root = json.parseToJsonElement(
            """
            {
              "contents": {"sectionListRenderer": {"contents": [
                {"musicShelfRenderer": {
                  "title": {"runs": [{"text": "Today"}]},
                  "contents": [
                    {"musicResponsiveListItemRenderer": {
                      "playlistItemData": {"videoId": "repeat"},
                      "flexColumns": [{"musicResponsiveListItemFlexColumnRenderer": {
                        "text": {"runs": [{"text": "Song"}]}
                      }}]
                    }},
                    {"musicResponsiveListItemRenderer": {
                      "playlistItemData": {"videoId": "repeat"},
                      "flexColumns": [{"musicResponsiveListItemFlexColumnRenderer": {
                        "text": {"runs": [{"text": "Song"}]}
                      }}]
                    }}
                  ]
                }},
                {"musicShelfRenderer": {
                  "title": {"runs": [{"text": "Yesterday"}]},
                  "contents": [{"musicResponsiveListItemRenderer": {
                    "playlistItemData": {"videoId": "older"},
                    "flexColumns": [{"musicResponsiveListItemFlexColumnRenderer": {
                      "text": {"runs": [{"text": "Older song"}]}
                    }}]
                  }}]
                }}
              ]}},
              "continuations": [{"nextContinuationData": {"continuation": "next-page"}}]
            }
            """.trimIndent(),
        )

        val page = InnertubeParser.parseHistoryPage(root)

        assertEquals(listOf("Today", "Yesterday"), page.sections.map { it.title })
        assertEquals(listOf("repeat", "repeat", "older"), page.songs.map { it.videoId })
        assertEquals("next-page", page.continuation)
    }

    @Test
    fun parsesContinuationShelfWithoutLosingRepeatedRows() {
        val root = json.parseToJsonElement(
            """
            {
              "continuationContents": {"musicShelfContinuation": {
                "contents": [{"musicResponsiveListItemRenderer": {
                  "playlistItemData": {"videoId": "repeat"},
                  "flexColumns": [{"musicResponsiveListItemFlexColumnRenderer": {
                    "text": {"runs": [{"text": "Song"}]}
                  }}]
                }}]
              }},
              "continuations": [{"nextContinuationData": {"continuation": "after-next"}}]
            }
            """.trimIndent(),
        )

        val page = InnertubeParser.parseHistoryPage(root)

        assertEquals(listOf("repeat"), page.songs.map { it.videoId })
        assertEquals("", page.sections.single().title)
        assertEquals("after-next", page.continuation)
    }
}

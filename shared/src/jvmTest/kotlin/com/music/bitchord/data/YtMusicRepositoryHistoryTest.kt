package com.music.bitchord.data

import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.innertube.buildPlaybackTrackingRequestBody
import com.music.bitchord.data.innertube.potoken.PoTokenGenerator
import com.music.bitchord.data.innertube.potoken.PoTokenMinter
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class YtMusicRepositoryHistoryTest {

    @Test
    fun signedOutRecentsDoNotRequestHistory() = runBlocking {
        var requested = false

        val result = fetchRecentHistory<String>(null) {
            requested = true
            listOf("track")
        }

        assertEquals(emptyList(), result)
        assertEquals(false, requested)
    }

    @Test
    fun authenticatedRecentsPreserveHistoryApiFailures() = runBlocking {
        val failure = IllegalStateException("UNAUTHENTICATED: LOGIN_REQUIRED")

        val thrown = assertFailsWith<IllegalStateException> {
            fetchRecentHistory<String>("session-cookie") { throw failure }
        }

        assertEquals(failure, thrown)
    }

    @Test
    fun authenticatedRecentsReturnAnActuallyEmptyHistory() = runBlocking {
        val result = fetchRecentHistory("session-cookie") { emptyList<String>() }

        assertEquals(emptyList(), result)
    }

    @Test
    fun datedHistoryFollowsContinuationAndCarriesItsApproximateDay() = runBlocking {
        val zone = ZoneId.of("UTC")
        val now = LocalDate.of(2026, 10, 29).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val yesterday = LocalDate.of(2026, 10, 28)
        val firstPage = Json.parseToJsonElement(
            """
            {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Yesterday"}]},
                  "contents": [${historyRow("first", "First track")}],
                  "continuations": [{"nextContinuationData": {"continuation": "page-2"}}]
                }
              }]
            }
            """.trimIndent(),
        ) as JsonObject
        val secondPage = Json.parseToJsonElement(
            """
            {
              "continuationContents": {
                "musicShelfContinuation": {
                  "contents": [${historyRow("second", "Second track")}]
                }
              }
            }
            """.trimIndent(),
        ) as JsonObject
        var requests = 0

        val result = collectPagedYtMusicHistory(firstPage, now, zone) { token ->
            requests++
            assertEquals("page-2", token)
            secondPage
        }

        val expectedDayAnchor = yesterday.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(1, requests, "The terminal page must not trigger another request")
        assertEquals(listOf("first", "second"), result.map { it.song.videoId })
        assertEquals(listOf(expectedDayAnchor, expectedDayAnchor), result.map { it.playedAtMillis })
        assertFalse(result.any { it.exact }, "Innertube date buckets never provide exact play times")
    }

    @Test
    fun incrementalDatedHistoryStopsAfterTheOneDayOverlapAndKeepsOnlyRecentRows() = runBlocking {
        val zone = ZoneId.of("UTC")
        val now = LocalDate.of(2026, 10, 29).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val lastSync = LocalDate.of(2026, 10, 28).atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
        val firstPage = Json.parseToJsonElement(
            """
            {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Yesterday"}]},
                  "contents": [${historyRow("recent", "Recent track")}],
                  "continuations": [{"nextContinuationData": {"continuation": "page-2"}}]
                }
              }]
            }
            """.trimIndent(),
        ) as JsonObject
        val olderPage = Json.parseToJsonElement(
            """
            {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Oct 26, 2026"}]},
                  "contents": [${historyRow("old", "Old track")}],
                  "continuations": [{"nextContinuationData": {"continuation": "page-3"}}]
                }
              }]
            }
            """.trimIndent(),
        ) as JsonObject
        var requests = 0

        val result = collectPagedYtMusicHistory(
            firstPage = firstPage,
            nowMillis = now,
            zone = zone,
            notBeforeMillis = lastSync,
        ) { token ->
            requests++
            assertEquals("page-2", token)
            olderPage
        }

        assertEquals(1, requests, "Older history pages must not be fetched after crossing the overlap window")
        assertEquals(listOf("recent"), result.map { it.song.videoId })
    }

    @Test
    fun quickPickExclusionsIncludeUndatedRowsAndTracksFoundOnLaterPages() = runBlocking {
        val firstPage = Json.parseToJsonElement(
            """
            {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Older"}]},
                  "contents": [${historyRow("played-undated", "Played without supported date")}],
                  "continuations": [{"nextContinuationData": {"continuation": "page-2"}}]
                }
              }]
            }
            """.trimIndent(),
        ) as JsonObject
        val secondPage = Json.parseToJsonElement(
            """
            {
              "continuationContents": {
                "musicShelfContinuation": {
                  "contents": [${historyRow("played-on-page-two", "Played later track")}]
                }
              }
            }
            """.trimIndent(),
        ) as JsonObject

        val history = collectPagedYtMusicHistorySongs(firstPage) { token ->
            assertEquals("page-2", token)
            secondPage
        }
        assertEquals(
            listOf("played-undated", "played-on-page-two"),
            history.map { it.videoId },
        )
        val excluded = mergeQuickPickExclusions(
            localHistoryIds = emptySet(),
            accountHistoryIds = history.mapNotNullTo(HashSet()) {
                it.videoId.takeIf(String::isNotBlank)
            },
        )
        val recommendations = unplayedRecommendations(
            listOf(
                Song("played-undated", "Played without supported date", "Artist", null),
                Song("played-on-page-two", "Played later track", "Artist", null),
                Song("fresh", "Fresh track", "Artist", null),
            ),
            excludedSongIds = excluded,
        )

        assertEquals(listOf("fresh"), recommendations.map(Song::videoId))
    }

    @Test
    fun unknownFinalDateBucketDoesNotInheritPreviousDateAcrossContinuation() = runBlocking {
        val zone = ZoneId.of("UTC")
        val now = LocalDate.of(2026, 10, 29).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val firstPage = Json.parseToJsonElement(
            """
            {
              "contents": [
                {
                  "musicShelfRenderer": {
                    "title": {"runs": [{"text": "Yesterday"}]},
                    "contents": [${historyRow("dated", "Dated track")}]
                  }
                },
                {
                  "musicShelfRenderer": {
                    "title": {"runs": [{"text": "Older"}]},
                    "contents": [${historyRow("unknown", "Unknown date track")}],
                    "continuations": [{"nextContinuationData": {"continuation": "older-page"}}]
                  }
                }
              ]
            }
            """.trimIndent(),
        ) as JsonObject
        val continuation = Json.parseToJsonElement(
            """
            {
              "continuationContents": {
                "musicShelfContinuation": {
                  "contents": [${historyRow("unknown-next", "Unknown continuation track")}]
                }
              }
            }
            """.trimIndent(),
        ) as JsonObject

        val result = collectPagedYtMusicHistory(firstPage, now, zone) { token ->
            assertEquals("older-page", token)
            continuation
        }

        assertEquals(listOf("dated"), result.map { it.song.videoId })
    }

    @Test
    fun datedHistoryRejectsRepeatedContinuationTokensInsteadOfReturningPartialResults() = runBlocking {
        val zone = ZoneId.of("UTC")
        val now = LocalDate.of(2026, 10, 29).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val firstPage = Json.parseToJsonElement(
            """
            {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Yesterday"}]},
                  "contents": [${historyRow("first", "First track")}],
                  "continuations": [{"nextContinuationData": {"continuation": "page-2"}}]
                }
              }]
            }
            """.trimIndent(),
        ) as JsonObject
        val repeatedPage = Json.parseToJsonElement(
            """
            {
              "continuationContents": {
                "musicShelfContinuation": {
                  "contents": [${historyRow("second", "Second track")}],
                  "continuations": [{"nextContinuationData": {"continuation": "page-2"}}]
                }
              }
            }
            """.trimIndent(),
        ) as JsonObject

        val failure = assertFailsWith<IllegalStateException> {
            collectPagedYtMusicHistory(firstPage, now, zone) { repeatedPage }
        }

        assertTrue(failure.message.orEmpty().contains("repeated a continuation token"))
    }

    @Test
    fun unrecognizedOlderHeadingStillImportsRowsWithAnApproximateDay() {
        val zone = ZoneId.of("UTC")
        val now = LocalDate.of(2026, 10, 29).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val yesterday = LocalDate.of(2026, 10, 28)
        val response = Json.parseToJsonElement(
            """
            {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Older"}]},
                  "contents": [${historyRow("unknown", "Unknown date track")}]
                }
              }]
            }
            """.trimIndent(),
        )

        val entries = InnertubeParser.collectHistoryEntries(response, now, zone, yesterday)

        assertEquals(listOf("unknown"), entries.map { it.song.videoId })
        assertEquals(
            LocalDate.of(2026, 10, 27).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            entries.single().playedAtMillis,
        )
    }

    @Test
    fun weekdayAndMonthDateHeadingIsParsed() {
        val zone = ZoneId.of("UTC")
        val now = LocalDate.of(2026, 10, 29).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val response = Json.parseToJsonElement(
            """
            {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Sunday, October 25"}]},
                  "contents": [${historyRow("weekday-date", "Weekday date track")}]
                }
              }]
            }
            """.trimIndent(),
        )

        val entries = InnertubeParser.collectHistoryEntries(response, now, zone)

        assertEquals(listOf("weekday-date"), entries.map { it.song.videoId })
        assertEquals(
            LocalDate.of(2026, 10, 25).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
            entries.single().playedAtMillis,
        )
    }

    @Test
    fun datedHistoryContinuationPreservesApiErrors() = runBlocking {
        val response = Json.parseToJsonElement(
            """{"error":{"code":401,"status":"UNAUTHENTICATED","message":"Invalid credentials"}}""",
        ) as JsonObject

        val failure = assertFailsWith<IllegalStateException> {
            collectPagedYtMusicHistory(
                firstPage = response,
                nowMillis = 1L,
                zone = ZoneId.of("UTC"),
            ) { error("An API error must not trigger continuation requests") }
        }
        assertEquals(true, failure.message.orEmpty().contains("401"))
    }

    @Test
    fun rawHistoryPreservesRepeatedRowsForTheSameVideo() {
        val response = Json.parseToJsonElement(
            """
            {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Today"}]},
                  "contents": [
                    ${historyRow("same-video", "Repeated track")},
                    ${historyRow("same-video", "Repeated track")}
                  ]
                }
              }]
            }
            """.trimIndent(),
        )

        val songs = InnertubeParser.collectHistorySongs(response)

        assertEquals(listOf("same-video", "same-video"), songs.map { it.videoId })
    }

    @Test
    fun pagedRawHistoryKeepsRepeatedListenRows() = runBlocking {
        val response = Json.parseToJsonElement(
            """
            {
              "contents": [{
                "musicShelfRenderer": {
                  "title": {"runs": [{"text": "Today"}]},
                  "contents": [
                    ${historyRow("repeat", "Repeated track")},
                    ${historyRow("repeat", "Repeated track")}
                  ]
                }
              }]
            }
            """.trimIndent(),
        ) as JsonObject

        val songs = collectPagedYtMusicHistorySongs(response) { error("No continuation expected") }

        assertEquals(listOf("repeat", "repeat"), songs.map { it.videoId })
    }

    private fun historyRow(videoId: String, title: String): String =
        """
        {
          "musicResponsiveListItemRenderer": {
            "playlistItemData": {"videoId": "$videoId"},
            "flexColumns": [
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": {"runs": [{"text": "$title"}]}
                }
              },
              {
                "musicResponsiveListItemFlexColumnRenderer": {
                  "text": {"runs": [{"text": "Artist"}]}
                }
              }
            ],
            "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [{"url": "https://example.test/thumb", "width": 120, "height": 120}]}}}
          }
        }
        """.trimIndent()

    @Test
    fun cancellableFetchRethrowsCancellationInsteadOfReturningFallback() = runBlocking {
        val cancellation = CancellationException("caller navigated away")

        val thrown = assertFailsWith<CancellationException> {
            runCatchingCancellable<String> { throw cancellation }
        }

        assertEquals(cancellation, thrown)
    }

    @Test
    fun cancellableFetchKeepsOrdinaryFailuresRecoverable() = runBlocking {
        val failure = IllegalStateException("temporary network failure")

        val result = runCatchingCancellable<String> { throw failure }

        assertEquals(failure, result.exceptionOrNull())
    }
    @Test
    fun accountPlaybackTrackingRequestIncludesCurrentSignatureAndWebPoToken() {
        val body = buildPlaybackTrackingRequestBody(
            videoId = "abcdefghijk",
            signatureTimestamp = 12345,
            playerPoToken = "player-request-token",
        )

        assertEquals("abcdefghijk", body["videoId"]?.jsonPrimitive?.content)
        assertEquals(
            12345,
            body["playbackContext"]?.jsonObject
                ?.get("contentPlaybackContext")?.jsonObject
                ?.get("signatureTimestamp")?.jsonPrimitive?.contentOrNull?.toInt(),
        )
        assertEquals(
            "player-request-token",
            body["serviceIntegrityDimensions"]?.jsonObject
                ?.get("poToken")?.jsonPrimitive?.contentOrNull,
        )
    }

    @Test
    fun accountPlaybackTrackingRequestKeepsPoTokenWhenSignatureTimestampIsUnavailable() {
        val body = buildPlaybackTrackingRequestBody(
            videoId = "abcdefghijk",
            signatureTimestamp = null,
            playerPoToken = "player-request-token",
        )

        assertEquals(
            "player-request-token",
            body["serviceIntegrityDimensions"]?.jsonObject
                ?.get("poToken")?.jsonPrimitive?.content,
        )
        assertEquals(
            null,
            body["playbackContext"]?.jsonObject
                ?.get("contentPlaybackContext")?.jsonObject
                ?.get("signatureTimestamp"),
        )
    }

    @Test
    fun webPoTokensAreAssignedToTheCorrectRequestScopes() = runBlocking {
        val generator = PoTokenGenerator(
            createMinter = {
                object : PoTokenMinter {
                    override val isExpired = false
                    override val isDead = false
                    override suspend fun generatePoToken(identifier: String) = "token-for-$identifier"
                    override fun close() = Unit
                }
            },
        )

        val tokens = generator.getWebClientPoToken("video-id", "visitor-id")
        assertEquals("token-for-video-id", tokens?.playerRequestPoToken)
        assertEquals("token-for-visitor-id", tokens?.streamingDataPoToken)
        generator.close()
    }

}

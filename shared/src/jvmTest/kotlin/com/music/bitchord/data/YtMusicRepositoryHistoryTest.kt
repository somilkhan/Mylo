package com.music.bitchord.data

import com.music.bitchord.data.innertube.InnertubeParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
                  "contents": [${historyRow("second", "Second track")}],
                  "continuations": [{"nextContinuationData": {"continuation": "page-2"}}]
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
        assertEquals(1, requests, "A repeated continuation token must stop pagination")
        assertEquals(listOf("first", "second"), result.map { it.song.videoId })
        assertEquals(listOf(expectedDayAnchor, expectedDayAnchor), result.map { it.playedAtMillis })
        assertFalse(result.any { it.exact }, "Innertube date buckets never provide exact play times")
    }

    @Test
    fun unsupportedHistoryHeadingDoesNotInheritAnotherPagesDate() {
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

        assertEquals(emptyList(), entries)
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
            ]
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
}

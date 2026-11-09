package com.music.bitchord.data.generator

import java.nio.file.Files
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayHistoryStoreTest {
    private val zone = ZoneId.of("UTC")
    private val day = 1_798_761_600_000L // 2027-01-01T00:00:00Z

    private fun store() = PlayHistoryStore(
        Files.createTempDirectory("mylo-history-test").resolve("history.tsv").toFile(),
        zone,
    )

    private fun play(
        source: PlaySource = PlaySource.YOUTUBE_MUSIC,
        at: Long = day,
        exact: Boolean = false,
        id: String = "track",
    ) = Play(id, "Title", "Artist", source, at, exact)

    @Test
    fun importingSameTrackDayTwiceAddsOnlyOneImportedRow() {
        val store = store()
        assertEquals(1, store.append(listOf(play())))
        assertEquals(0, store.append(listOf(play(at = day + 60_000))))
        assertEquals(1, store.readAll().size)
    }

    @Test
    fun distinctExactMyloListensOnSameDayAreRetained() {
        val store = store()
        assertEquals(2, store.append(listOf(
            play(PlaySource.MYLO, day, true),
            play(PlaySource.MYLO, day + 120_000, true),
        )))
        assertEquals(listOf(day, day + 120_000), store.readAll().map { it.atMillis })
    }

    @Test
    fun exactMyloListenReplacesImportedApproximationAndSignalsContentChange() {
        val store = store()
        store.append(listOf(play()))
        val result = store.appendWithResult(listOf(play(PlaySource.MYLO, day + 90_000, true)))

        assertEquals(0, result.addedCount)
        assertTrue(result.changed)
        assertEquals(1, store.readAll().size)
        assertEquals(PlaySource.MYLO, store.readAll().single().source)
        assertEquals(day + 90_000, store.readAll().single().atMillis)
    }

    @Test
    fun keyedImportedRepeatListensSurviveAndReimportIdempotently() {
        val store = store()
        val repeated = listOf(
            play(id = "repeat").copy(sourceEventKey = "account:day:0"),
            play(id = "repeat", at = day + 1).copy(sourceEventKey = "account:day:1"),
        )

        assertEquals(2, store.append(repeated))
        assertEquals(0, store.append(repeated))
        assertEquals(2, store.readAll().size)
        assertEquals(setOf("account:day:0", "account:day:1"), store.readAll().mapNotNull { it.sourceEventKey }.toSet())
    }

    @Test
    fun legacyImportedRowsStillCollapseAndExactMyloOverridesImportedEvents() {
        val store = store()
        store.append(listOf(play(), play(at = day + 1)))
        assertEquals(1, store.readAll().size)

        store.append(listOf(play(PlaySource.MYLO, day + 90_000, true)))
        assertEquals(1, store.readAll().size)
        assertEquals(PlaySource.MYLO, store.readAll().single().source)
    }

    @Test
    fun exactMyloPlayDoesNotEraseKeyedYoutubeRepeatEvents() {
        val store = store()
        store.append(listOf(
            play(id = "repeat").copy(sourceEventKey = "account:day:0"),
            play(id = "repeat", at = day + 1).copy(sourceEventKey = "account:day:1"),
        ))

        assertEquals(1, store.append(listOf(play(PlaySource.MYLO, day + 90_000, true, "repeat"))))
        assertEquals(3, store.readAll().size)
        assertEquals(2, store.readAll().count { it.source == PlaySource.YOUTUBE_MUSIC })
        assertEquals(1, store.readAll().count { it.source == PlaySource.MYLO })
    }

    @Test
    fun forgetImportedKeepsMyloEvents() {
        val store = store()
        store.append(listOf(play(), play(PlaySource.MYLO, day + 60_000, true, "mylo")))
        store.forgetImported()

        assertEquals(listOf("mylo"), store.readAll().map { it.videoId })
        assertFalse(store.readAll().any { it.source == PlaySource.YOUTUBE_MUSIC })
    }
}

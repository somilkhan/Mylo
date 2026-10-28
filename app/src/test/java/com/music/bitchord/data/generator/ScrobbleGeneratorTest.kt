package com.music.bitchord.data.generator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId

class ScrobbleGeneratorTest {

    private val zone: ZoneId = ZoneId.of("UTC")
    private val day = 86_400_000L
    private val now = 1_000 * day

    private fun play(
        id: String,
        artist: String = "A",
        source: PlaySource = PlaySource.MYLO,
        daysAgo: Double = 0.0,
        exact: Boolean = true,
    ) = Play(id, "Title $id", artist, source, now - (daysAgo * day).toLong(), exact)

    @Test
    fun sameSongSameDay_keepsExactMyloPlay() {
        val plays = listOf(
            play("x", source = PlaySource.YOUTUBE_MUSIC, exact = false),
            play("x", source = PlaySource.MYLO, exact = true),
        )
        val out = dedupePlays(plays, zone)
        assertEquals(1, out.size)
        assertEquals(PlaySource.MYLO, out.single().source)
    }

    @Test
    fun topTracks_ranksByPlayCountAndRecency() {
        val plays = listOf(
            play("a", daysAgo = 1.0), play("a", daysAgo = 2.0), play("a", daysAgo = 3.0),
            play("b", artist = "B", daysAgo = 0.0),
        )
        val out = ScrobbleGenerator.topTracks(plays, now, zone)
        assertEquals("id:a", out.first().play.songKey())
        assertEquals(3, out.first().plays)
    }

    @Test
    fun topTracks_capsSongsPerArtist() {
        val plays = listOf("a", "b", "c", "d").map { play(it, artist = "Same") }
        val out = ScrobbleGenerator.topTracks(plays, now, zone, maxPerArtist = 2)
        assertEquals(2, out.size)
        assertTrue(out.all { it.play.artist == "Same" })
    }

    @Test
    fun approximatePlays_weighLessThanExact() {
        val exact = ScrobbleGenerator.weight(play("e", exact = true), now)
        val approx = ScrobbleGenerator.weight(play("e", exact = false), now)
        assertTrue(approx < exact)
    }

    @Test
    fun store_roundTripsLocalUriAndKeepsDistinctLocalFilesSeparate() {
        val file = File.createTempFile("local-plays", ".tsv")
        file.deleteOnExit()
        val store = PlayHistoryStore(file, zone)
        val first = play("").copy(localUri = "content://media/external/audio/1")
        val second = play("").copy(localUri = "content://media/external/audio/2")

        assertEquals(2, store.append(listOf(first, second)))
        assertEquals(listOf(first, second), store.readAll())
    }

    @Test
    fun store_readsLegacyRowsWithoutLocalUri() {
        val file = File.createTempFile("legacy-plays", ".tsv")
        file.deleteOnExit()
        file.writeText("x\tTitle x\tA\tMYLO\t${now}\ttrue\n")

        val stored = PlayHistoryStore(file, zone).readAll()

        assertEquals(1, stored.size)
        assertEquals(null, stored.single().localUri)
    }

    @Test
    fun store_roundTripsAndDedupesOnWrite() {
        val file = File.createTempFile("plays", ".tsv")
        file.deleteOnExit()
        val store = PlayHistoryStore(file, zone)
        val batch = listOf(play("a"), play("b", artist = "B"))
        assertEquals(2, store.append(batch))
        assertEquals(0, store.append(batch))
        assertEquals(2, store.readAll().size)
        store.forget()
        assertTrue(store.readAll().isEmpty())
    }
}

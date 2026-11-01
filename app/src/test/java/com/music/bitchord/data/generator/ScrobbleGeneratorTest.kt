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
    fun recent_returnsNewestUniqueTracksInsideWindow() {
        val plays = listOf(
            play("old", daysAgo = 10.0),
            play("newer", daysAgo = 1.0),
            play("older", daysAgo = 6.0),
        )

        val out = ScrobbleGenerator.recent(plays, now, zone, days = 7)

        assertEquals(listOf("id:newer", "id:older"), out.map { it.play.songKey() })
    }

    @Test
    fun recentAndTopTracks_ignoreFutureDatedRows() {
        val plays = listOf(
            play("past", daysAgo = 1.0),
            play("future").copy(atMillis = now + day),
        )

        val recent = ScrobbleGenerator.recent(plays, now, zone, days = 7)
        val top = ScrobbleGenerator.topTracks(plays, now, zone, windowDays = 7)

        assertEquals(listOf("id:past"), recent.map { it.play.songKey() })
        assertEquals(listOf("id:past"), top.map { it.play.songKey() })
    }

    @Test
    fun rediscover_requiresRepeatedPlaysAndAQuietPeriod() {
        val plays = listOf(
            play("rediscover", daysAgo = 120.0),
            play("rediscover", daysAgo = 100.0),
            play("rediscover", daysAgo = 80.0),
            play("too-recent", daysAgo = 10.0),
            play("only-once", daysAgo = 100.0),
        )

        val out = ScrobbleGenerator.rediscover(
            plays,
            now,
            zone,
            quietDays = 60,
            minPlays = 3,
        )

        assertEquals(listOf("id:rediscover"), out.map { it.play.songKey() })
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
        // LocalMediaRepository may attach a matched YouTube video id to a file.
        // The content URI must remain the identity in that case.
        val first = play("matched-video").copy(localUri = "content://media/external/audio/1")
        val second = play("matched-video").copy(localUri = "content://media/external/audio/2")

        assertEquals(2, store.append(listOf(first, second)))
        assertEquals(listOf(first, second), store.readAll())
    }

    @Test
    fun dedupePlays_preservesDistinctMyloRepeatListensOnSameDay() {
        val first = play("repeat", daysAgo = 1.0)
        val second = first.copy(atMillis = first.atMillis + 60 * 60 * 1000L)
        val duplicateEvent = first.copy(source = PlaySource.MYLO)

        val result = dedupePlays(listOf(first, second, duplicateEvent), zone)

        assertEquals(listOf(first.atMillis, second.atMillis), result.map { it.atMillis })
    }

    @Test
    fun dedupePlays_prefersAllMyloEventsOverOverlappingImportedRows() {
        val first = play("repeat", daysAgo = 1.0)
        val second = first.copy(atMillis = first.atMillis + 60 * 60 * 1000L)
        val imported = first.copy(source = PlaySource.YOUTUBE_MUSIC, exact = false)

        val result = dedupePlays(listOf(imported, first, second), zone)

        assertEquals(listOf(first, second), result)
    }

    @Test
    fun recent_dedupesSameSongOnTheSameLocalDay() {
        val exact = play("same", daysAgo = 1.0)
        val importedDuplicate = exact.copy(source = PlaySource.YOUTUBE_MUSIC, exact = false)
        val previousDay = play("same", daysAgo = 2.0)

        val result = ScrobbleGenerator.recent(
            listOf(exact, importedDuplicate, previousDay),
            now,
            zone,
        )

        assertEquals(2, result.single().plays)
    }

    @Test
    fun rediscover_doesNotTreatSameDayDuplicatesAsRepeatedListens() {
        val first = play("same", daysAgo = 100.0)
        val duplicate = first.copy(source = PlaySource.YOUTUBE_MUSIC, exact = false)
        val secondDay = play("same", daysAgo = 90.0)

        val result = ScrobbleGenerator.rediscover(
            listOf(first, duplicate, secondDay),
            now,
            zone,
            quietDays = 60,
            minPlays = 3,
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun localPathTakesPrecedenceOverMatchedVideoIdWhenUriIsUnavailable() {
        val first = play("same-video").copy(localPath = "/storage/music/one.flac")
        val second = play("same-video").copy(localPath = "/storage/music/two.flac")

        assertTrue(first.songKey() != second.songKey())
        assertEquals(2, dedupePlays(listOf(first, second), zone).size)
    }

    @Test
    fun store_roundTripsLocalPathForPathBackedPlayback() {
        val file = File.createTempFile("local-path-plays", ".tsv")
        file.deleteOnExit()
        val store = PlayHistoryStore(file, zone)
        val original = play("matched-video").copy(localPath = "/storage/music/offline.flac")

        assertEquals(1, store.append(listOf(original)))
        assertEquals(original, store.readAll().single())
    }

    @Test
    fun store_readsLegacyRowsWithoutLocalPath() {
        val file = File.createTempFile("legacy-local-plays", ".tsv")
        file.deleteOnExit()
        file.writeText("x\tTitle x\tA\tMYLO\t${now}\ttrue\tcontent://audio/1\thttps://img.example/cover.jpg\n")

        val stored = PlayHistoryStore(file, zone).readAll()

        assertEquals(1, stored.size)
        assertEquals(null, stored.single().localPath)
        assertEquals("content://audio/1", stored.single().localUri)
    }

    @Test
    fun localUriTakesPrecedenceOverMatchedVideoId() {
        val first = play("same-video").copy(localUri = "content://media/external/audio/1")
        val second = play("same-video").copy(localUri = "content://media/external/audio/2")

        assertTrue(first.songKey() != second.songKey())
        assertEquals(2, dedupePlays(listOf(first, second), zone).size)
    }

    @Test
    fun storeSkipsMalformedRowsAndLeavesNoTemporaryFileAfterWrite() {
        val file = File.createTempFile("malformed-plays", ".tsv")
        file.deleteOnExit()
        file.writeText("broken row" + System.lineSeparator())
        val store = PlayHistoryStore(file, zone)

        assertEquals(1, store.append(listOf(play("valid"))))
        assertEquals(listOf("valid"), store.readAll().map { it.videoId })
        assertTrue(!File(file.absolutePath + ".tmp").exists())
    }

    @Test
    fun store_roundTripsArtworkUrlWithoutBreakingLegacyLocalUriRows() {
        val file = File.createTempFile("artwork-plays", ".tsv")
        file.deleteOnExit()
        val store = PlayHistoryStore(file, zone)
        val original = play("artwork").copy(
            localUri = "content://media/external/audio/7",
            thumbnailUrl = "https://img.example/cover.jpg",
        )

        store.append(listOf(original))

        assertEquals(original, store.readAll().single())
    }

    @Test
    fun store_roundTripsGeneratedTrackMetadataAndKeepsLegacyRowsReadable() {
        val file = File.createTempFile("track-metadata-plays", ".tsv")
        file.deleteOnExit()
        val store = PlayHistoryStore(file, zone)
        val original = play("metadata").copy(
            thumbnailUrl = "https://img.example/cover.jpg",
            durationText = "3:42",
            artistId = "UCartist",
            albumId = "MPREalbum",
            albumName = "Album",
            isVideo = true,
            isVideoOrigin = true,
        )

        assertEquals(1, store.append(listOf(original)))
        assertEquals(original, store.readAll().single())

        file.writeText("legacy\tLegacy title\tLegacy artist\tMYLO\t${now}\ttrue\n")
        val legacy = store.readAll().single()
        assertEquals("Legacy title", legacy.title)
        assertEquals(null, legacy.durationText)
        assertEquals(null, legacy.albumName)
        assertEquals(false, legacy.isVideo)
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
    fun forgetImported_preservesMyloRecordedHistory() {
        val file = File.createTempFile("mixed-plays", ".tsv")
        file.deleteOnExit()
        val store = PlayHistoryStore(file, zone)
        val own = play("own")
        val imported = play("external", source = PlaySource.YOUTUBE_MUSIC, exact = false)
        store.append(listOf(own, imported))

        store.forgetImported()

        assertEquals(listOf(own), store.readAll())
    }

    @Test
    fun store_preservesDistinctMyloRepeatListensOnSameDay() {
        val file = File.createTempFile("repeat-listens", ".tsv")
        file.deleteOnExit()
        val store = PlayHistoryStore(file, zone)
        val first = play("repeat")
        val second = first.copy(atMillis = first.atMillis + 60 * 60 * 1000L)

        assertEquals(2, store.append(listOf(first, second)))
        assertEquals(listOf(first, second), store.readAll())
        assertEquals(0, store.append(listOf(first, second)))
        assertEquals(listOf(first, second), store.readAll())
    }

    @Test
    fun store_replacingImportedRowWithExactMyloPlayReportsChangeWithoutIncreasingCount() {
        val file = File.createTempFile("replace-imported-play", ".tsv")
        file.deleteOnExit()
        val store = PlayHistoryStore(file, zone)
        val imported = play("same", source = PlaySource.YOUTUBE_MUSIC, exact = false)
        val exactMylo = imported.copy(source = PlaySource.MYLO, exact = true)

        assertEquals(1, store.append(listOf(imported)))
        val result = store.appendWithResult(listOf(exactMylo))

        assertEquals(0, result.addedCount)
        assertTrue(result.changed)
        assertEquals(listOf(exactMylo), store.readAll())
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

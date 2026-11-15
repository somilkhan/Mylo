package com.music.bitchord.ui.screens.scrobble

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

private const val RADIO_SEED_LIMIT = 5
private const val RADIO_CONCURRENCY = 3
private const val RADIO_TIMEOUT_MS = 6_000L
private const val MAX_TRACKS_PER_SEED = 10
private const val MAX_TRACKS_PER_ARTIST = 3

/**
 * LastWave local-taste radio port. Mylo has no compatible LastWave Room DAOs,
 * so the engine ranks seeds from ListeningStats and uses Mylo's own
 * YouTube Music radio endpoint for the related-song queues.
 */
internal object ScrobbleLocalTasteEngine {
    suspend fun discover(total: Int): List<Song> = withContext(Dispatchers.IO) {
        if (total <= 0) return@withContext emptyList()

        val local = runCatching { ListeningStats.summary(ReplayPeriod.ALL_TIME) }
            .getOrElse { error ->
                if (error is CancellationException) throw error
                null
            }
        val history = safeSongs { YtMusicRepository.history() }
        val recents = safeSongs { YtMusicRepository.recents() }
        val liked = safeLibrary()?.likedSongs.orEmpty()
        val heard = (local?.songs.orEmpty().map { it.song } + history + recents + liked)
            .distinctScrobbleSongs()
        val seeds = heard.filter { it.videoId.isNotBlank() }.take(RADIO_SEED_LIMIT)

        if (seeds.isEmpty()) {
            val quickPicks = safeSongs { YtMusicRepository.quickPicks() }
            return@withContext buildLocalTasteCandidates(
                seeds = emptyList(),
                radioBySeed = listOf(quickPicks),
                heardSongs = heard,
                total = total,
            )
        }

        val limiter = Semaphore(RADIO_CONCURRENCY)
        val radioBySeed = coroutineScope {
            seeds.map { seed ->
                async {
                    limiter.withPermit {
                        withTimeoutOrNull(RADIO_TIMEOUT_MS) {
                            safeSongs { YtMusicRepository.radio(seed.videoId) }
                        }.orEmpty()
                    }
                }
            }.awaitAll()
        }

        val curated = buildLocalTasteCandidates(
            seeds = seeds,
            radioBySeed = radioBySeed,
            heardSongs = heard,
            total = total,
        )
        if (curated.size >= total) return@withContext curated

        val quickPicks = safeSongs {
            YtMusicRepository.quickPicks(
                excludeSongIds = heard.mapTo(mutableSetOf()) { it.videoId } +
                    curated.map { it.videoId },
            )
        }
        curated + buildLocalTasteCandidates(
            seeds = emptyList(),
            radioBySeed = listOf(quickPicks),
            heardSongs = heard + curated,
            total = total - curated.size,
        )
    }

    private suspend fun safeLibrary() = try {
        YtMusicRepository.library().getOrNull()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private suspend fun safeSongs(
        call: suspend () -> Result<List<Song>>,
    ): List<Song> = try {
        call().getOrDefault(emptyList())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Pure curation stage: exclude heard songs and alternate edits, reject
 * compilation/junk uploads, deduplicate, then cap each seed and artist.
 */
internal fun buildLocalTasteCandidates(
    seeds: List<Song>,
    radioBySeed: List<List<Song>>,
    heardSongs: List<Song>,
    total: Int,
): List<Song> {
    if (total <= 0) return emptyList()

    val seedIds = seeds.mapTo(HashSet()) { it.videoId }
    val seedKeys = seeds.mapTo(HashSet()) { it.scrobbleIdentityKey }
    val heardIds = heardSongs.mapTo(HashSet()) { it.videoId }
    val heardKeys = heardSongs.mapTo(HashSet()) { it.scrobbleIdentityKey }
    val heardBaseKeys = heardSongs.mapTo(HashSet()) { baseTrackKey(it.title, it.artist) }
    val seenIds = HashSet<String>()
    val seenKeys = HashSet<String>()
    val seenBaseKeys = HashSet<String>()
    val artistCounts = HashMap<String, Int>()
    val result = ArrayList<Song>(total)

    for (seedRows in radioBySeed) {
        var acceptedForSeed = 0
        for (song in seedRows) {
            if (result.size >= total || acceptedForSeed >= MAX_TRACKS_PER_SEED) break
            if (song.videoId.isBlank() || song.title.isBlank() || song.artist.isBlank()) continue
            if (song.videoId in seedIds || song.scrobbleIdentityKey in seedKeys) continue
            if (song.videoId in heardIds || song.scrobbleIdentityKey in heardKeys) continue
            if (isLocalTasteJunk(song.title)) continue
            val baseKey = baseTrackKey(song.title, song.artist)
            if (baseKey in heardBaseKeys || baseKey in seenBaseKeys) continue

            val artistKey = song.artist.normalizeScrobbleText().lowercase(Locale.ROOT)
            if ((artistCounts[artistKey] ?: 0) >= MAX_TRACKS_PER_ARTIST) continue
            if (song.videoId in seenIds || song.scrobbleIdentityKey in seenKeys) continue

            result += song
            acceptedForSeed++
            seenIds += song.videoId
            seenKeys += song.scrobbleIdentityKey
            seenBaseKeys += baseKey
            artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
        }
        if (result.size >= total) break
    }
    return result
}

private val editSuffix = Regex(
    """\s*[\(\[\-]?\s*(official video|official audio|official music video|lyrics?|lyric video|audio|visualizer|remastered|remaster|live version|live|radio edit|extended mix|karaoke|hd|4k)\s*[\)\]]?\s*$""",
    RegexOption.IGNORE_CASE,
)

private fun baseTrackKey(title: String, artist: String): String {
    var clean = title.normalizeScrobbleText()
    var previous: String
    do {
        previous = clean
        clean = clean.replace(editSuffix, "").trim()
    } while (clean != previous)
    return clean.lowercase(Locale.ROOT) + "|" + artist.normalizeScrobbleText().lowercase(Locale.ROOT)
}

private fun isLocalTasteJunk(title: String): Boolean {
    val lower = title.lowercase()
    return listOf(
        "mashup", "mash up", "mash-up", "jukebox", "mega mix", "megamix",
        "non stop", "nonstop", "full album", "full songs", "compilation",
        "slowed + reverb", "slowed reverb", "slowed & reverb", "bass boosted",
        "8d audio", "karaoke", "ringtone", "instrumental", "1 hour", "10 hour",
        "clean version", "sped up",
    ).any(lower::contains)
}

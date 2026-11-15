package com.music.bitchord.ui.screens

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Dependency-adapted LastWave LocalTasteSuggestionEngine.
 *
 * Mylo already records trusted listening history and exposes YouTube Music's
 * watch-next radio endpoint, so reuse those rather than importing LastWave's
 * Room DAOs, exclusion tables or duplicate playback models.
 */
internal object ScrobbleLocalTasteEngine {
    private const val MAX_SEEDS = 5
    private const val SOURCE_TIMEOUT_MILLIS = 6_000L

    suspend fun discover(
        seedTracks: List<Song>,
        heardSongs: List<Song>,
        limit: Int,
    ): List<Song> = coroutineScope {
        if (limit <= 0) return@coroutineScope emptyList()
        val seeds = seedTracks.asSequence()
            .filter(::isYouTubeCatalogTrack)
            .distinctBy { it.videoId }
            .take(MAX_SEEDS)
            .toList()
        if (seeds.isEmpty()) return@coroutineScope emptyList()

        val slots = Semaphore(3)
        val relatedBySeed = seeds.map { seed ->
            async {
                slots.withPermit {
                    try {
                        withTimeoutOrNull(SOURCE_TIMEOUT_MILLIS) {
                            YtMusicRepository.radio(seed.videoId).getOrThrow()
                        }.orEmpty()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
            }
        }.awaitAll()

        buildLocalTasteCandidates(seeds, relatedBySeed, heardSongs, limit)
    }
}

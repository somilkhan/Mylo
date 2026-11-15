package com.music.bitchord.ui.screens.scrobble

import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ScrobbleStatsRepository
import com.music.bitchord.data.stats.ScrobbleStatsSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout

internal enum class ScrobbleGenerateMode(val storageValue: String) {
    TOP("top"),
    LIBRARY("library"),
    RECENT("recent"),
    SIMILAR_TRACKS("similar-tracks"),
    SIMILAR_ARTISTS("similar-artists"),
    TAG("tag"),
    MIX("mix"),
    RECOMMENDATIONS("recommendations"),
    NEVER_HEARD("never-heard"),
}

/** LastWave generator mode orchestration adapted to Mylo's existing Song/playback APIs. */
internal object ScrobbleGeneratorRepository {
    suspend fun generate(
        mode: ScrobbleGenerateMode,
        count: Int,
        period: String = "overall",
        seedTrack: Song? = null,
        seedArtist: String = "",
        tagInput: String = "",
        onProgress: (String) -> Unit = {},
    ): List<Song> = withTimeout(80_000L) {
        val target = count.coerceIn(5, 35)
        val local = safeLocalSummary()
        val history = safeSongs { YtMusicRepository.history() }
        val recents = safeSongs { YtMusicRepository.recents() }
        val library = safeLibrary()
        val liked = library?.likedSongs.orEmpty()
        val heard = (local?.songs.orEmpty().map { it.song } + history + recents + liked)
            .distinctScrobbleSongs()
        val seeds = heard.filter { it.videoId.isNotBlank() }.take(5)
        val username = AppSettings.lastfmUsername.value.trim()

        onProgress("Analyzing listening taste…")
        val needsLastFm = mode in setOf(
            ScrobbleGenerateMode.TOP,
            ScrobbleGenerateMode.SIMILAR_TRACKS,
            ScrobbleGenerateMode.SIMILAR_ARTISTS,
            ScrobbleGenerateMode.TAG,
            ScrobbleGenerateMode.RECOMMENDATIONS,
            ScrobbleGenerateMode.NEVER_HEARD,
        )
        val profile = if (needsLastFm) loadProfile(username, period) else null

        when (mode) {
            ScrobbleGenerateMode.TOP -> {
                val chartSongs = ScrobbleRecommendationEngine.resolveChartEntries(
                    profile?.tracks.orEmpty(), target,
                )
                chartSongs.ifEmpty {
                    local?.songs.orEmpty().map { it.song }.ifEmpty {
                        safeSongs { YtMusicRepository.quickPicks() }
                    }
                }.take(target)
            }
            ScrobbleGenerateMode.LIBRARY -> (liked + library?.librarySongs.orEmpty())
                .distinctScrobbleSongs().ifEmpty { local?.songs.orEmpty().map { it.song } }.take(target)
            ScrobbleGenerateMode.RECENT -> (history + recents)
                .distinctScrobbleSongs().ifEmpty { local?.songs.orEmpty().map { it.song } }.take(target)
            ScrobbleGenerateMode.SIMILAR_TRACKS -> {
                val seed = requireNotNull(seedTrack) { "Choose a track to find similar songs" }
                ScrobbleRecommendationEngine.generateSimilarTracks(seed, profile, heard, target)
                    .ifEmpty { ScrobbleLocalTasteEngine.discover(target, seeds, heard) }
            }
            ScrobbleGenerateMode.SIMILAR_ARTISTS -> {
                require(seedArtist.isNotBlank()) { "Enter an artist to find similar artists" }
                ScrobbleRecommendationEngine.generateSimilarArtists(seedArtist.trim(), profile, heard, target)
                    .ifEmpty { ScrobbleLocalTasteEngine.discover(target, seeds, heard) }
            }
            ScrobbleGenerateMode.TAG -> {
                require(tagInput.isNotBlank()) { "Enter a genre or tag" }
                ScrobbleRecommendationEngine.generateTagTracks(tagInput.trim(), profile, heard, target)
                    .ifEmpty { ScrobbleLocalTasteEngine.discover(target, seeds, heard) }
            }
            ScrobbleGenerateMode.MIX -> {
                onProgress("Building a mix from your listening taste…")
                ScrobbleLocalTasteEngine.discover(target, seeds, heard)
            }
            ScrobbleGenerateMode.RECOMMENDATIONS,
            ScrobbleGenerateMode.NEVER_HEARD -> ScrobbleRecommendationEngine.generate(
                profile = profile,
                seeds = seeds,
                heardSongs = heard,
                total = target,
                freshOnly = mode == ScrobbleGenerateMode.NEVER_HEARD,
                onProgress = onProgress,
            )
        }
    }

    private suspend fun loadProfile(username: String, period: String): ScrobbleStatsSnapshot? {
        if (username.isBlank() || !LastFM.isInitialized()) return null
        return try {
            ScrobbleStatsRepository.load(username, period)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun safeLocalSummary() = try {
        ListeningStats.summary(ReplayPeriod.ALL_TIME)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private suspend fun safeLibrary() = try {
        YtMusicRepository.library().getOrNull()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    private suspend fun safeSongs(call: suspend () -> Result<List<Song>>): List<Song> = try {
        call().getOrDefault(emptyList())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        emptyList()
    }
}

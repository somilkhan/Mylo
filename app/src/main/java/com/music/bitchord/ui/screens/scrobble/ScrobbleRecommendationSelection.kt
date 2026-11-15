package com.music.bitchord.ui.screens.scrobble

import com.music.bitchord.data.model.Song
import java.util.Locale
import kotlin.math.ceil

/** Recommendation-only signals stay out of Mylo's shared playback Song model. */
internal data class ScrobbleRecommendationCandidate(
    val song: Song,
    val tags: Set<String> = emptySet(),
    val fresh: Boolean = true,
    val familiar: Boolean = false,
    val score: Double = 0.0,
)

/** LastWave RecommendationEngine.selectFinal/interleave, adapted to Song. */
internal object ScrobbleRecommendationSelection {
    private const val ARTIST_CAP = 2
    private const val ALBUM_CAP = 2
    private const val GENRE_CAP_RATIO = 0.35

    fun select(candidates: List<ScrobbleRecommendationCandidate>, total: Int): List<Song> {
        if (total <= 0 || candidates.isEmpty()) return emptyList()
        val unique = distinctCandidates(candidates)
            .sortedWith(compareByDescending<ScrobbleRecommendationCandidate> { it.fresh }
                .thenByDescending { it.score })
        val selected = selectFinal(unique, total)
            .distinctBy { it.song.scrobbleIdentityKey }
            .take(total)
        if (selected.size < 3) return selected.map { it.song }

        val interleaved = interleave(selected)
        val ranked = selected.sortedByDescending { it.score }
        val opener = ranked.first()
        val closer = ranked.firstOrNull {
            it.song.scrobbleIdentityKey != opener.song.scrobbleIdentityKey
        } ?: ranked[1]

        return interleaved.filterNot {
            it.song.scrobbleIdentityKey == opener.song.scrobbleIdentityKey ||
                it.song.scrobbleIdentityKey == closer.song.scrobbleIdentityKey
        }.let { middle -> listOf(opener) + middle + closer }.map { it.song }
    }

    private fun distinctCandidates(candidates: List<ScrobbleRecommendationCandidate>): List<ScrobbleRecommendationCandidate> {
        val seenIds = HashSet<String>()
        val seenKeys = HashSet<String>()
        return candidates.filter { candidate ->
            val key = candidate.song.scrobbleIdentityKey
            val id = candidate.song.videoId.takeIf(String::isNotBlank)
            if (key in seenKeys || (id != null && id in seenIds)) false else {
                seenKeys += key
                id?.let(seenIds::add)
                true
            }
        }
    }

    private fun selectFinal(
        scored: List<ScrobbleRecommendationCandidate>,
        total: Int,
    ): List<ScrobbleRecommendationCandidate> {
        val genreCap = maxOf(2, ceil(total * GENRE_CAP_RATIO).toInt())

        fun attempt(
            artistCap: Int,
            albumCapOn: Boolean,
            genreCapOn: Boolean,
            familiarCap: Int,
        ): List<ScrobbleRecommendationCandidate> {
            val artistCounts = mutableMapOf<String, Int>()
            val albumCounts = mutableMapOf<String, Int>()
            val genreCounts = mutableMapOf<String, Int>()
            val pickedKeys = mutableSetOf<String>()
            val picked = mutableListOf<ScrobbleRecommendationCandidate>()
            var familiarCount = 0

            for (candidate in scored) {
                val song = candidate.song
                val key = song.scrobbleIdentityKey
                if (key in pickedKeys || !candidate.fresh) continue
                if (candidate.familiar && familiarCount >= familiarCap) continue

                val artistKey = song.artist.normalizeScrobbleText().lowercase(Locale.ROOT)
                if ((artistCounts[artistKey] ?: 0) >= artistCap) continue

                val album = song.albumName?.trim()?.takeIf(String::isNotEmpty)
                val albumKey = album?.lowercase(Locale.ROOT)
                if (albumCapOn && albumKey != null &&
                    (albumCounts[albumKey] ?: 0) >= ALBUM_CAP
                ) continue

                val genreTag = candidate.tags.firstOrNull { it.startsWith("tag:") }
                if (genreCapOn && genreTag != null &&
                    (genreCounts[genreTag] ?: 0) >= genreCap
                ) continue

                artistCounts[artistKey] = (artistCounts[artistKey] ?: 0) + 1
                if (albumKey != null) albumCounts[albumKey] = (albumCounts[albumKey] ?: 0) + 1
                if (genreCapOn && genreTag != null) {
                    genreCounts[genreTag] = (genreCounts[genreTag] ?: 0) + 1
                }
                pickedKeys += key
                picked += candidate
                if (candidate.familiar) familiarCount++
                if (picked.size >= total) break
            }
            return picked
        }

        val familiarCap = maxOf(1, total / 6)
        var result = attempt(ARTIST_CAP, true, true, familiarCap)
        if (result.size < total) result = attempt(3, true, true, familiarCap)
        if (result.size < total) result = attempt(3, true, false, familiarCap)
        if (result.size < total) result = attempt(3, false, false, familiarCap)
        if (result.size < total) result = attempt(Int.MAX_VALUE, false, false, familiarCap)
        if (result.size < total) result = attempt(Int.MAX_VALUE, false, false, maxOf(familiarCap, total / 3))
        if (result.size < total) result = attempt(Int.MAX_VALUE, false, false, total)
        return result
    }

    private fun interleave(
        candidates: List<ScrobbleRecommendationCandidate>,
    ): List<ScrobbleRecommendationCandidate> {
        val buckets = LinkedHashMap<String, ArrayDeque<ScrobbleRecommendationCandidate>>()
        for (candidate in candidates) {
            val genre = candidate.tags.firstOrNull { it.startsWith("tag:") } ?: "other"
            buckets.getOrPut(genre) { ArrayDeque() }.addLast(candidate)
        }
        val labels = buckets.keys.toList()
        val result = ArrayList<ScrobbleRecommendationCandidate>(candidates.size)
        var previous: String? = null
        while (result.size < candidates.size) {
            var index = labels.indexOfFirst { label ->
                buckets.getValue(label).isNotEmpty() && label != previous
            }
            if (index < 0) index = labels.indexOfFirst { buckets.getValue(it).isNotEmpty() }
            if (index < 0) break
            val label = labels[index]
            result += buckets.getValue(label).removeFirst()
            previous = label
        }
        return result
    }
}

package com.music.bitchord.ui.screens.scrobble

import com.music.bitchord.data.model.Song
import java.util.Locale

/**
 * LastWave's GeneratedTrack identity/deduplication port, adapted to Mylo's
 * immutable Song model. Video IDs are the strongest identity; normalized
 * title/artist is the cross-source fallback.
 */
internal fun String.normalizeScrobbleText(): String {
    val trimmed = trim()
    var needsWork = false
    for (i in trimmed.indices) {
        val c = trimmed[i]
        if (c.isScrobbleWhitespace() &&
            (c != ' ' || (i + 1 < trimmed.length && trimmed[i + 1].isScrobbleWhitespace()))
        ) {
            needsWork = true
            break
        }
    }
    if (!needsWork) return trimmed

    val out = StringBuilder(trimmed.length)
    var inRun = false
    for (c in trimmed) {
        if (c.isScrobbleWhitespace()) {
            if (!inRun) out.append(' ')
            inRun = true
        } else {
            out.append(c)
            inRun = false
        }
    }
    return out.toString()
}

private fun Char.isScrobbleWhitespace(): Boolean =
    this == ' ' || this == '\t' || this == '\n' ||
        this == '\u000B' || this == '\u000C' || this == '\r'

internal val Song.scrobbleIdentityKey: String
    get() = "${title.normalizeScrobbleText()}|${artist.normalizeScrobbleText()}".lowercase(Locale.ROOT)

/** Same video wins even when catalogue titles differ; otherwise compare names. */
internal fun Song.sameScrobbleSongAs(other: Song): Boolean {
    if (videoId.isNotBlank() && videoId == other.videoId) return true
    if (title.isBlank() && other.title.isBlank()) return false
    if (scrobbleIdentityKey == other.scrobbleIdentityKey) return true
    return title.trim().equals(other.title.trim(), ignoreCase = true) &&
        artist.trim().equals(other.artist.trim(), ignoreCase = true)
}

/**
 * O(n) first-wins deduplication, matching LastWave's hash-set strategy.
 * Keeping the first Song preserves its original artwork and navigation metadata.
 */
internal fun List<Song>.distinctScrobbleSongs(): List<Song> {
    if (size < 2) return this
    val result = ArrayList<Song>(size)
    val videoIds = HashSet<String>(size * 2)
    val identityKeys = HashSet<String>(size * 2)
    val namePairs = HashSet<Pair<String, String>>(size * 2)

    for (song in this) {
        val videoId = song.videoId.takeIf(String::isNotBlank)
        if (videoId != null && videoId in videoIds) continue

        val named = song.title.isNotBlank()
        val pair = song.title.trim().foldScrobbleCase() to song.artist.trim().foldScrobbleCase()
        val key = song.scrobbleIdentityKey
        if (named && (key in identityKeys || pair in namePairs)) continue

        result += song
        videoId?.let(videoIds::add)
        if (named) {
            identityKeys += key
            namePairs += pair
        }
    }
    return result
}

/** Mirrors String.equals(ignoreCase = true), including non-ASCII case pairs. */
private fun String.foldScrobbleCase(): String {
    val folded = CharArray(length)
    for (index in indices) {
        folded[index] = Character.toLowerCase(Character.toUpperCase(this[index]))
    }
    return String(folded)
}

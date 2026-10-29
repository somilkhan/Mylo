package com.music.bitchord.data.generator

import java.time.ZoneId
import kotlin.math.exp

/** One generated result: the song, how much it counts, and why it is here. */
data class Ranked(
    val play: Play,
    val plays: Int,
    val score: Double,
    val reason: String,
)

/**
 * Builds playlists from a play history. Pure functions, so every mode can be
 * tested on the JVM.
 *
 * Smartness here is three things. Recent plays count more than old ones, with
 * a half-life of about 30 days. Mylo's exact plays count more than approximate
 * imported ones. And no artist fills more than [maxPerArtist] slots, so one
 * singer does not take over a mix.
 */
object ScrobbleGenerator {

    private const val HALF_LIFE_DAYS = 30.0
    private const val APPROX_WEIGHT = 0.6

    /** Songs played most in the last [windowDays], weighted by recency. */
    fun topTracks(
        plays: List<Play>,
        nowMillis: Long,
        zone: ZoneId,
        windowDays: Int = 90,
        limit: Int = 25,
        maxPerArtist: Int = 2,
    ): List<Ranked> {
        val inWindow = plays.filter {
            it.atMillis <= nowMillis && nowMillis - it.atMillis <= windowDays * DAY_MS
        }
        return rankSongs(inWindow, nowMillis, zone, limit, maxPerArtist) { count ->
            "Played $count times in $windowDays days"
        }
    }

    /** What was played in the last [days]. Most recent first, one row per song. */
    fun recent(
        plays: List<Play>,
        nowMillis: Long,
        zone: ZoneId,
        days: Int = 7,
        limit: Int = 25,
    ): List<Ranked> {
        val inWindow = plays.filter {
            it.atMillis <= nowMillis && nowMillis - it.atMillis <= days * DAY_MS
        }
        return inWindow
            .groupBy { it.songKey() }
            .map { (_, group) -> group.maxBy { it.atMillis } to group.size }
            .sortedByDescending { it.first.atMillis }
            .take(limit)
            .map { (play, count) ->
                Ranked(play, count, 1.0, "Played in the last $days days")
            }
    }

    /** Songs loved before, quiet for at least [quietDays], with at least [minPlays] plays overall. */
    fun rediscover(
        plays: List<Play>,
        nowMillis: Long,
        zone: ZoneId,
        quietDays: Int = 60,
        minPlays: Int = 3,
        limit: Int = 25,
        maxPerArtist: Int = 2,
    ): List<Ranked> {
        val byKey = plays.groupBy { it.songKey() }
        val candidates = byKey.values.filter { group ->
            group.size >= minPlays && nowMillis - group.maxOf { it.atMillis } >= quietDays * DAY_MS
        }.flatten()
        return rankSongs(candidates, nowMillis, zone, limit, maxPerArtist) { count ->
            "Played $count times, quiet for $quietDays+ days"
        }
    }

    private fun rankSongs(
        plays: List<Play>,
        nowMillis: Long,
        zone: ZoneId,
        limit: Int,
        maxPerArtist: Int,
        reason: (Int) -> String,
    ): List<Ranked> {
        val deduped = dedupePlays(plays, zone)
        val scored = deduped
            .groupBy { it.songKey() }
            .map { (_, group) ->
                val score = group.sumOf { weight(it, nowMillis) }
                val representative = group.maxBy { weight(it, nowMillis) }
                Ranked(representative, group.size, score, reason(group.size))
            }
            .sortedByDescending { it.score }
        return applyArtistCap(scored, maxPerArtist).take(limit)
    }

    /** Recency decay times source weight. */
    internal fun weight(play: Play, nowMillis: Long): Double {
        val ageDays = ((nowMillis - play.atMillis).coerceAtLeast(0L)).toDouble() / DAY_MS
        val decay = exp(-ageDays * kotlin.math.ln(2.0) / HALF_LIFE_DAYS)
        val sourceWeight = if (play.exact) 1.0 else APPROX_WEIGHT
        return decay * sourceWeight
    }

    /** Keeps the list in order, but drops any song past [maxPerArtist] for its artist. */
    internal fun applyArtistCap(ranked: List<Ranked>, maxPerArtist: Int): List<Ranked> {
        val seen = HashMap<String, Int>()
        val out = ArrayList<Ranked>()
        for (item in ranked) {
            val artist = normaliseText(item.play.artist)
            val used = seen[artist] ?: 0
            if (used < maxPerArtist) {
                out.add(item)
                seen[artist] = used + 1
            }
        }
        return out
    }

    private const val DAY_MS = 86_400_000L
}

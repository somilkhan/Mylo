package com.music.bitchord.data.scrobbling

import kotlin.math.roundToLong

/**
 * Pure scrobbling timing rules, kept free of Android and coroutines so they can be unit-tested.
 * Rule (Last.fm): a track counts when it is longer than the minimum, and it is scrobbled once
 * it has played for the earlier of [percent] of its length or [maxSeconds].
 */
internal object ScrobbleTiming {

    /** Parses "M:SS", "MM:SS" or "H:MM:SS". Returns null when the text is not a duration. */
    fun parseDurationSeconds(text: String): Int? {
        val parts = text.trim().split(":")
        if (parts.size !in 2..3) return null
        val numbers = parts.map { part -> part.toIntOrNull()?.takeIf { it >= 0 } ?: return null }
        return if (numbers.size == 3) {
            numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
        } else {
            numbers[0] * 60 + numbers[1]
        }
    }

    fun isEligible(durationSeconds: Int, minSeconds: Int): Boolean = durationSeconds > minSeconds

    fun thresholdMillis(durationSeconds: Int, percent: Float, maxSeconds: Int): Long {
        val fraction = (durationSeconds * 1000L * percent).roundToLong()
        return minOf(fraction, maxSeconds * 1000L)
    }

    /**
     * Whether a play counts as a listen. An unknown duration counts once the play reaches the
     * time cap, the conservative choice when the track length cannot be checked.
     */
    fun isListenCountable(
        playedMillis: Long,
        durationSeconds: Int?,
        minSeconds: Int = DEFAULT_MIN_SECONDS,
        percent: Float = DEFAULT_PERCENT,
        maxSeconds: Int = DEFAULT_MAX_SECONDS,
    ): Boolean {
        if (durationSeconds == null) return playedMillis >= maxSeconds * 1000L
        if (!isEligible(durationSeconds, minSeconds)) return false
        return playedMillis >= thresholdMillis(durationSeconds, percent, maxSeconds)
    }

    const val DEFAULT_MIN_SECONDS = 30
    const val DEFAULT_PERCENT = 0.5f
    const val DEFAULT_MAX_SECONDS = 180
}

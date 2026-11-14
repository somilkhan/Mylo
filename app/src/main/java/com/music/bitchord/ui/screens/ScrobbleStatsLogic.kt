package com.music.bitchord.ui.screens

import kotlin.math.roundToInt

internal data class ListeningDayPart(
    val name: String,
    val timeRange: String,
    val listeningMs: Long,
) {
    fun percentageOf(totalListeningMs: Long): Int =
        if (totalListeningMs <= 0L) 0
        else ((listeningMs.toDouble() / totalListeningMs.toDouble()) * 100).roundToInt()
}

internal fun buildListeningDayParts(hourlyListeningMs: List<Long>): List<ListeningDayPart> {
    fun sumHours(startHour: Int, endExclusive: Int): Long =
        (startHour until endExclusive).sumOf { hour ->
            hourlyListeningMs.getOrNull(hour)?.coerceAtLeast(0L) ?: 0L
        }

    return listOf(
        ListeningDayPart("Night", "00–05", sumHours(0, 6)),
        ListeningDayPart("Morning", "06–11", sumHours(6, 12)),
        ListeningDayPart("Afternoon", "12–17", sumHours(12, 18)),
        ListeningDayPart("Evening", "18–23", sumHours(18, 24)),
    )
}

package com.music.bitchord.ui.scrobble.stats

import com.music.bitchord.data.stats.ReplayPeriod

/** Plain, warm copy for the Stats tab. Sentences here make no claim the data cannot back. */
internal object StatsCopy {
    const val LISTENING_TIME = "Listening time"
    const val TOP_TRACKS = "Top tracks"

    fun periodSentence(period: ReplayPeriod): String = when (period) {
        ReplayPeriod.THIS_WEEK -> "Here is how your week has sounded so far."
        ReplayPeriod.THIS_MONTH -> "Here is how your month has sounded so far."
        ReplayPeriod.THIS_YEAR -> "Here is how your year has sounded so far."
        ReplayPeriod.ALL_TIME -> "Here is everything you have played, from the start."
    }
}

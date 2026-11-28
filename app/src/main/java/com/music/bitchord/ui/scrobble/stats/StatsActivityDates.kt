package com.music.bitchord.ui.scrobble.stats

import com.music.bitchord.data.scrobbling.LastFmActivityBucket
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Rolling bars are elapsed-time ranges, not necessarily one listener calendar day. */
internal fun activityBucketDates(bucket: LastFmActivityBucket, zone: ZoneId): Pair<LocalDate, LocalDate> =
    Instant.ofEpochSecond(bucket.startEpochSeconds).atZone(zone).toLocalDate() to
        Instant.ofEpochSecond(bucket.endEpochSeconds).atZone(zone).toLocalDate()

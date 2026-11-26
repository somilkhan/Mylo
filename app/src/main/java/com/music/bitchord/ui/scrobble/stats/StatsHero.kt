package com.music.bitchord.ui.scrobble.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.ui.replay.formatListening
import com.music.bitchord.ui.replay.replayCount
import com.music.bitchord.ui.theme.AccentRed

/**
 * The single focal point of the tab: the period switcher, the listening time as the
 * main figure, plays as the line under it, and one warm sentence.
 */
@Composable
internal fun StatsHero(
    period: ReplayPeriod,
    onPeriodSelect: (ReplayPeriod) -> Unit,
    summary: ReplaySummary?,
    plays: Int,
    loading: Boolean,
    reduceMotion: Boolean,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val alpha = rememberSkeletonAlpha(reduceMotion)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatsPeriodSwitcher(selected = period, onSelect = onPeriodSelect)
        Spacer(Modifier.height(18.dp))
        Text(
            StatsCopy.LISTENING_TIME,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        if (loading) {
            SkeletonBlock(alpha, Modifier.fillMaxWidth(0.6f).height(56.dp), RoundedCornerShape(12.dp))
            SkeletonBlock(alpha, Modifier.fillMaxWidth(0.3f).height(14.dp))
        } else {
            val figure = summary?.let { formatListening(context, it.totalMs) }
            Text(
                figure ?: stringResource(R.string.scrobble_not_recorded_yet),
                style = if (figure != null) {
                    MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1.0).sp)
                } else {
                    MaterialTheme.typography.titleLarge
                },
                color = colors.onSurface,
            )
            Text(
                context.replayCount(plays, R.plurals.replay_play_count),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Text(
            StatsCopy.periodSentence(period),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Entry to the Replay stories. Red is used here and nowhere else on the tab. */
@Composable
internal fun StatsReplayEntry(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val title = stringResource(R.string.your_replay)
    val subtitle = stringResource(R.string.replay_subtitle)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$title. $subtitle" }
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceVariant,
        contentColor = colors.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentRed),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

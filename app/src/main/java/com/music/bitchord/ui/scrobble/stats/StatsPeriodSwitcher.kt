package com.music.bitchord.ui.scrobble.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.stats.ReplayPeriod

/**
 * Segmented control for the statistics period. At large font scales the labels stop
 * fitting in equal slots, so the control scrolls sideways instead of clipping them.
 */
@Composable
internal fun StatsPeriodSwitcher(
    selected: ReplayPeriod,
    onSelect: (ReplayPeriod) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val scrollable = LocalDensity.current.fontScale >= 1.3f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceContainerHigh)
            .border(
                width = 1.dp,
                color = colors.outline.copy(alpha = 0.18f),
                shape = RoundedCornerShape(50),
            )
            .padding(4.dp)
            .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .semantics { contentDescription = "Time period" }
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReplayPeriod.entries.forEach { period ->
            val isSelected = period == selected
            Box(
                modifier = Modifier
                    .then(if (scrollable) Modifier.defaultMinSize(minWidth = 96.dp) else Modifier.weight(1f))
                    .height(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) colors.tertiary else colors.surfaceContainerHigh)
                    .semantics(mergeDescendants = true) {}
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(period) },
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = period.chip,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) colors.onTertiary else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

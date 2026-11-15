package com.music.bitchord.ui.screens.scrobble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.stats.ListeningStats
import com.music.bitchord.data.stats.ReplayPeriod
import com.music.bitchord.data.stats.ReplaySummary
import com.music.bitchord.data.stats.ScrobbleChartEntry
import com.music.bitchord.data.stats.ScrobbleStatsRepository
import com.music.bitchord.data.stats.ScrobbleStatsSnapshot
import kotlinx.coroutines.CancellationException

private data class StatsPeriodOption(val label: String, val value: String)

private val statsPeriods = listOf(
    StatsPeriodOption("All time", "overall"),
    StatsPeriodOption("12 months", "12month"),
    StatsPeriodOption("6 months", "6month"),
    StatsPeriodOption("3 months", "3month"),
    StatsPeriodOption("Month", "1month"),
    StatsPeriodOption("7 days", "7day"),
)

/**
 * A separate stats surface: Mylo's private on-device Replay totals remain the
 * local source of truth; Last.fm charts are an additional account-backed view.
 */
@Composable
internal fun ScrobbleStatsScreen(
    modifier: Modifier = Modifier,
) {
    var selectedPeriod by rememberSaveable { mutableStateOf("overall") }
    var localSummary by remember { mutableStateOf<ReplaySummary?>(null) }
    var remote by remember { mutableStateOf<ScrobbleStatsSnapshot?>(null) }
    var localLoading by remember { mutableStateOf(true) }
    var remoteLoading by remember { mutableStateOf(false) }
    var remoteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        localLoading = true
        localSummary = try {
            ListeningStats.summary(ReplayPeriod.ALL_TIME)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        localLoading = false
    }

    val username = AppSettings.lastfmUsername.value.trim()
    LaunchedEffect(username, selectedPeriod) {
        remote = null
        remoteError = null
        if (username.isBlank() || !LastFM.isInitialized()) return@LaunchedEffect
        remoteLoading = true
        try {
            remote = ScrobbleStatsRepository.load(username, selectedPeriod)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            remoteError = error.message ?: "Couldn't load Last.fm charts"
        } finally {
            remoteLoading = false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 20.dp, bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Your listening", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "On-device listening and your Last.fm charts, kept separate and clearly labelled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text("MYLO · ALL TIME", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            when {
                localLoading -> CircularProgressIndicator()
                localSummary == null -> Text(
                    "Local listening stats aren't available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                localSummary!!.isEmpty -> Text(
                    "Play some music and your listening stats will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LocalStatsSummary(localSummary!!)
            }
        }
        item {
            Spacer(Modifier.height(2.dp))
            Text("LAST.FM CHARTS", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                statsPeriods.take(3).forEach { option ->
                    FilterChip(
                        selected = selectedPeriod == option.value,
                        onClick = { selectedPeriod = option.value },
                        label = { Text(option.label) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                statsPeriods.drop(3).forEach { option ->
                    FilterChip(
                        selected = selectedPeriod == option.value,
                        onClick = { selectedPeriod = option.value },
                        label = { Text(option.label) },
                    )
                }
            }
        }
        item {
            when {
                username.isBlank() || !LastFM.isInitialized() -> Text(
                    "Connect Last.fm in Account & Scrobbling to see account charts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                remoteLoading -> CircularProgressIndicator()
                remoteError != null -> Text(
                    remoteError!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                remote != null -> {
                    val data = remote!!
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${data.totalScrobbles} total scrobbles · @${data.username}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        StatsChartSection("Top tracks", data.tracks)
                        StatsChartSection("Top artists", data.artists)
                        StatsChartSection("Top albums", data.albums)
                        StatsChartSection("Top tags", data.tags)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalStatsSummary(summary: ReplaySummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LocalStatTile("Listening", "${summary.hours}h")
        LocalStatTile("Plays", summary.totalPlays.toString())
        LocalStatTile("Tracks", summary.distinctSongs.toString())
    }
    Spacer(Modifier.height(8.dp))
    summary.artists.take(5).forEachIndexed { index, artist ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${index + 1}. ${artist.title}", style = MaterialTheme.typography.bodyMedium)
            Text("${artist.plays}", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RowScope.LocalStatTile(label: String, value: String) {
    Card(modifier = Modifier.weight(1f)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatsChartSection(
    title: String,
    entries: List<ScrobbleChartEntry>,
) {
    if (entries.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            entries.take(8).forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${index + 1}. ${entry.name}", style = MaterialTheme.typography.bodyMedium)
                        entry.artist?.takeIf(String::isNotBlank)?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(
                        (entry.playCount.takeIf { it > 0 } ?: entry.listeners ?: 0L).toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

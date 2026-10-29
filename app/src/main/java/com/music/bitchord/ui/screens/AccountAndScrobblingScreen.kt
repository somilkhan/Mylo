package com.music.bitchord.ui.screens

import com.music.bitchord.auth.clearSpotifyWebSession
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.HorizontalDivider
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.spotify_logo
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.music.bitchord.data.generator.ScrobbleHistory
import com.music.bitchord.data.generator.ScrobbleHistorySync
import java.text.DateFormat
import java.util.Date
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.model.Account
import com.music.bitchord.data.settings.AppSettings
import kotlin.math.roundToInt

@Composable
fun AccountAndScrobblingScreen(
    signedIn: Boolean,
    account: Account?,
    channelName: String?,
    onSignIn: () -> Unit,
    onSwitchChannel: () -> Unit,
    onSignOut: () -> Unit,
    onOpenListenBrainzLogin: () -> Unit,
    onOpenLastfmLogin: () -> Unit,
    onOpenDiscord: () -> Unit,
    onOpenSpotify: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val lastfmEnabled by AppSettings.lastfmEnabled.collectAsStateWithLifecycle()
    val lastfmUsername by AppSettings.lastfmUsername.collectAsStateWithLifecycle()
    val lastfmSessionKey by AppSettings.lastfmSessionKey.collectAsStateWithLifecycle()
    val lastfmScrobbleEnabled by AppSettings.lastfmScrobbleEnabled.collectAsStateWithLifecycle()
    val lastfmNowPlayingEnabled by AppSettings.lastfmNowPlaying.collectAsStateWithLifecycle()
    val lastfmPrimaryArtistOnly by AppSettings.lastfmPrimaryArtistOnly.collectAsStateWithLifecycle()
    val scrobbleMinDuration by AppSettings.scrobbleMinDuration.collectAsStateWithLifecycle()
    val scrobbleDelayPercent by AppSettings.scrobbleDelayPercent.collectAsStateWithLifecycle()
    val scrobbleDelaySeconds by AppSettings.scrobbleDelaySeconds.collectAsStateWithLifecycle()
    val listenBrainzEnabled by AppSettings.listenBrainzEnabled.collectAsStateWithLifecycle()
    val listenBrainzToken by AppSettings.listenBrainzToken.collectAsStateWithLifecycle()
    val listenBrainzPrimaryArtistOnly by AppSettings.listenBrainzPrimaryArtistOnly.collectAsStateWithLifecycle()
    val discordToken by AppSettings.discordToken.collectAsStateWithLifecycle()
    val discordUsername by AppSettings.discordUsername.collectAsStateWithLifecycle()
    val discordRpcEnabled by AppSettings.discordRpcEnabled.collectAsStateWithLifecycle()
    val spotifyConnected by AppSettings.spotifySpdcToken.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    val historySyncPrefs = remember(appContext) {
        appContext.getSharedPreferences(ScrobbleHistorySync.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }
    var historySyncEnabled by remember(historySyncPrefs) {
        mutableStateOf(historySyncPrefs.getBoolean(ScrobbleHistorySync.KEY_ENABLED, false))
    }
    var historySyncStatus by remember(historySyncPrefs) {
        mutableStateOf(historySyncPrefs.getString(ScrobbleHistorySync.KEY_LAST_STATUS, null))
    }
    var historyLastSyncAt by remember(historySyncPrefs) {
        mutableStateOf(historySyncPrefs.getLong(ScrobbleHistorySync.KEY_LAST_SYNC_AT, 0L))
    }
    var historyLastAdded by remember(historySyncPrefs) {
        mutableStateOf(historySyncPrefs.getInt(ScrobbleHistorySync.KEY_LAST_ADDED, 0))
    }
    var showForgetImportedHistory by remember { mutableStateOf(false) }
    var historyActionMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    DisposableEffect(historySyncPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                ScrobbleHistorySync.KEY_ENABLED ->
                    historySyncEnabled = prefs.getBoolean(ScrobbleHistorySync.KEY_ENABLED, false)
                ScrobbleHistorySync.KEY_LAST_STATUS ->
                    historySyncStatus = prefs.getString(ScrobbleHistorySync.KEY_LAST_STATUS, null)
                ScrobbleHistorySync.KEY_LAST_SYNC_AT ->
                    historyLastSyncAt = prefs.getLong(ScrobbleHistorySync.KEY_LAST_SYNC_AT, 0L)
                ScrobbleHistorySync.KEY_LAST_ADDED ->
                    historyLastAdded = prefs.getInt(ScrobbleHistorySync.KEY_LAST_ADDED, 0)
            }
        }
        historySyncPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { historySyncPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        Text(
            text = stringResource(R.string.account_integrations),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 14.dp),
        )

        // One card: who is signed in, which profile is listening, and the way
        // out, with the explanation of profiles underneath.
        SettingsGroup(
            footer = if (signedIn) stringResource(R.string.account_profiles_help) else null,
            topSpacing = 0.dp,
        ) {
            AccountCard(
                signedIn = signedIn,
                account = account,
                onSignIn = onSignIn,
                onClick = onSwitchChannel,
                grouped = true,
            )
            if (signedIn) {
                FullWidthDivider()
                SettingsRow(
                    icon = Icons.Rounded.SwitchAccount,
                    title = stringResource(R.string.listen_as),
                    subtitle = channelName ?: stringResource(R.string.default_youtube_profile),
                    onClick = onSwitchChannel,
                )
                FullWidthDivider()
                DestructiveRow(label = stringResource(R.string.sign_out), onClick = onSignOut)
            }
        }

        SettingsGroup(
            header = "YouTube Music history",
            footer = "Imported history stays on this device. Automatic refresh runs on Wi-Fi about every six hours; imported rows are marked approximate.",
        ) {
            SettingsRow(
                icon = Icons.Rounded.History,
                title = "Background history sync",
                subtitle = historySyncSubtitle(historySyncEnabled, historySyncStatus, historyLastSyncAt, historyLastAdded),
                trailing = {
                    Switch(
                        checked = historySyncEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) ScrobbleHistorySync.schedule(appContext)
                            else ScrobbleHistorySync.cancel(appContext)
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                },
                onClick = {
                    if (historySyncEnabled) ScrobbleHistorySync.cancel(appContext)
                    else ScrobbleHistorySync.schedule(appContext)
                },
            )
            FullWidthDivider()
            SettingsRow(
                icon = Icons.Rounded.History,
                title = "Forget imported history",
                subtitle = "Remove imported listens only. Mylo's own listening history is kept.",
                onClick = { showForgetImportedHistory = true },
            )
            if (historyActionMessage != null) {
                Text(
                    text = historyActionMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        SettingsGroup(
            header = stringResource(R.string.spotify),
            footer = stringResource(R.string.spotify_connect_subtitle),
        ) {
            SettingsRow(
                iconPainter = painterResource(Res.drawable.spotify_logo),
                title = stringResource(R.string.spotify),
                subtitle = stringResource(
                    if (spotifyConnected.isNotBlank()) R.string.connected else R.string.tap_to_connect,
                ),
                onClick = onOpenSpotify,
            )
            if (spotifyConnected.isNotBlank()) {
                FullWidthDivider()
                DestructiveRow(
                    label = stringResource(R.string.spotify_disconnect),
                    onClick = {
                        clearSpotifyWebSession()
                        AppSettings.setSpotifySpdcToken("")
                    },
                )
            }
        }

        SettingsGroup(
            header = stringResource(R.string.rich_presence),
            footer = stringResource(R.string.rich_presence_footer),
        ) {
            SettingsRow(
                icon = ImageVector.vectorResource(R.drawable.ic_discord),
                title = "Discord",
                subtitle = when {
                    discordToken.isEmpty() -> stringResource(R.string.tap_to_connect)
                    !discordRpcEnabled -> stringResource(R.string.connected_presence_off)
                    discordUsername.isNotEmpty() -> stringResource(R.string.sharing_as, discordUsername)
                    else -> stringResource(R.string.sharing_listens)
                },
                onClick = onOpenDiscord,
            )
        }

        if (AppSettings.scrobblingAvailable) {
            SettingsGroup(
                header = stringResource(R.string.scrobbling),
                footer = stringResource(R.string.scrobbling_footer),
            ) {
                SettingsRow(
                    icon = Icons.Rounded.Cloud,
                    title = "ListenBrainz",
                    subtitle = if (listenBrainzEnabled && listenBrainzToken.isNotBlank()) {
                        stringResource(R.string.connected)
                    } else {
                        stringResource(R.string.enter_token_to_enable)
                    },
                    trailing = {
                        Switch(
                            checked = listenBrainzEnabled,
                            onCheckedChange = { checked ->
                                if (checked && listenBrainzToken.isBlank()) {
                                    onOpenListenBrainzLogin()
                                } else {
                                    AppSettings.setListenBrainzEnabled(checked)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = onOpenListenBrainzLogin,
                )
                if (listenBrainzEnabled && listenBrainzToken.isNotBlank()) {
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.scrobble_primary_artist_only),
                        subtitle = stringResource(R.string.scrobble_primary_artist_only_subtitle),
                        trailing = {
                            Switch(
                                checked = listenBrainzPrimaryArtistOnly,
                                onCheckedChange = AppSettings::setListenBrainzPrimaryArtistOnly,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        },
                        onClick = { AppSettings.setListenBrainzPrimaryArtistOnly(!listenBrainzPrimaryArtistOnly) },
                    )
                }
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.History,
                    title = "Last.fm",
                    subtitle = if (lastfmSessionKey.isNotBlank()) {
                        stringResource(R.string.signed_in_as, lastfmUsername)
                    } else {
                        stringResource(R.string.tap_to_sign_in)
                    },
                    trailing = {
                        Switch(
                            checked = lastfmEnabled,
                            onCheckedChange = { checked ->
                                if (checked && lastfmSessionKey.isBlank()) {
                                    onOpenLastfmLogin()
                                } else {
                                    AppSettings.setLastfmEnabled(checked)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                checkedBorderColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                    onClick = {
                        if (lastfmSessionKey.isNotBlank()) {
                            AppSettings.setLastfmSessionKey("")
                            AppSettings.setLastfmUsername("")
                            AppSettings.setLastfmEnabled(false)
                            AppSettings.setLastfmScrobbleEnabled(false)
                            AppSettings.setLastfmNowPlaying(false)
                        } else {
                            onOpenLastfmLogin()
                        }
                    },
                )
                if (lastfmEnabled && lastfmSessionKey.isNotBlank()) {
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.scrobble_tracks),
                        subtitle = stringResource(R.string.scrobble_tracks_subtitle),
                        trailing = {
                            Switch(
                                checked = lastfmScrobbleEnabled,
                                onCheckedChange = AppSettings::setLastfmScrobbleEnabled,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        },
                        onClick = { AppSettings.setLastfmScrobbleEnabled(!lastfmScrobbleEnabled) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.scrobble_primary_artist_only),
                        subtitle = stringResource(R.string.scrobble_primary_artist_only_subtitle),
                        trailing = {
                            Switch(
                                checked = lastfmPrimaryArtistOnly,
                                onCheckedChange = AppSettings::setLastfmPrimaryArtistOnly,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        },
                        onClick = { AppSettings.setLastfmPrimaryArtistOnly(!lastfmPrimaryArtistOnly) },
                    )
                    RowDivider()
                    SettingsRow(
                        icon = Icons.Rounded.GraphicEq,
                        title = stringResource(R.string.now_playing),
                        subtitle = stringResource(R.string.lastfm_now_playing_subtitle),
                        trailing = {
                            Switch(
                                checked = lastfmNowPlayingEnabled,
                                onCheckedChange = AppSettings::setLastfmNowPlaying,
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        },
                        onClick = { AppSettings.setLastfmNowPlaying(!lastfmNowPlayingEnabled) },
                    )
                }
            }

            if (lastfmEnabled && lastfmSessionKey.isNotBlank()) {
                SettingsGroup(header = stringResource(R.string.scrobble_timing)) {
                    SliderRow(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.min_song_duration),
                        subtitle = stringResource(R.string.min_song_duration_subtitle),
                        value = "${scrobbleMinDuration}s",
                        sliderValue = scrobbleMinDuration.toFloat(),
                        onSliderValue = { AppSettings.setScrobbleMinDuration(it.roundToInt()) },
                        valueRange = 15f..120f,
                        steps = 20,
                    )
                    RowDivider()
                    SliderRow(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.scrobble_delay),
                        subtitle = stringResource(R.string.scrobble_delay_subtitle),
                        value = "${(scrobbleDelayPercent * 100).roundToInt()}%",
                        sliderValue = scrobbleDelayPercent,
                        onSliderValue = { AppSettings.setScrobbleDelayPercent(it) },
                        valueRange = 0.1f..1.0f,
                        steps = 8,
                    )
                    RowDivider()
                    SliderRow(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.max_delay),
                        subtitle = stringResource(R.string.max_delay_subtitle),
                        value = "${scrobbleDelaySeconds}s",
                        sliderValue = scrobbleDelaySeconds.toFloat(),
                        onSliderValue = { AppSettings.setScrobbleDelaySeconds(it.roundToInt()) },
                        valueRange = 30f..300f,
                        steps = 26,
                    )
                }
            }
        } else {
            // Left in place rather than dropped: a section that simply vanishes
            // reads as a feature that never existed, and this one is coming back.
            // The rows are dimmed and inert via `enabled = false`.
            SettingsGroup(
                header = stringResource(R.string.scrobbling),
                footer = stringResource(R.string.scrobbling_paused_footer),
            ) {
                SettingsRow(
                    icon = Icons.Rounded.Cloud,
                    title = "ListenBrainz",
                    subtitle = stringResource(R.string.back_in_future_version),
                    enabled = false,
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Rounded.History,
                    title = "Last.fm",
                    subtitle = stringResource(R.string.back_in_future_version),
                    enabled = false,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showForgetImportedHistory) {
        AlertDialog(
            onDismissRequest = { showForgetImportedHistory = false },
            title = { Text("Forget imported history?") },
            text = { Text("This removes imported YouTube Music and other external listening rows from this device. Mylo-recorded listens will remain.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showForgetImportedHistory = false
                        scope.launch {
                            withContext(Dispatchers.IO) { ScrobbleHistory.forgetImported() }
                            historyActionMessage = "Imported history cleared."
                        }
                    },
                ) { Text("Forget imported history") }
            },
            dismissButton = {
                TextButton(onClick = { showForgetImportedHistory = false }) { Text("Cancel") }
            },
        )
    }
}

private fun historySyncSubtitle(
    enabled: Boolean,
    status: String?,
    lastSyncAt: Long,
    lastAdded: Int,
): String {
    if (!enabled) return "Paused"
    return when (status) {
        ScrobbleHistorySync.STATUS_WAITING_FOR_SIGN_IN ->
            "Waiting for YouTube Music sign-in"
        ScrobbleHistorySync.STATUS_WAITING_FOR_WIFI ->
            "Waiting for Wi-Fi"
        ScrobbleHistorySync.STATUS_RETRYING ->
            "Sync failed; will retry on Wi-Fi"
        ScrobbleHistorySync.STATUS_NO_DATED_ROWS ->
            "No date-labelled rows found on last sync"
        ScrobbleHistorySync.STATUS_SUCCESS -> {
            val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(lastSyncAt))
            "Wi-Fi only · Last sync $time · $lastAdded new listens"
        }
        else -> "Wi-Fi only · Sync about every six hours"
    }
}

/** A hairline across the whole card, for setting a destructive row apart from the rows above it. */
@Composable
private fun FullWidthDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline)
}

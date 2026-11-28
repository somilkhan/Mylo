package com.music.bitchord.ui.scrobble

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.auth.SignedIn
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.generate.PlaylistGenerationMode
import com.music.bitchord.data.generate.PlaylistGenerationRepository
import com.music.bitchord.data.generate.PlaylistGenerationRequest
import com.music.bitchord.data.generate.PlaylistGenerationRules
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.scrobbling.LastFmStatsPeriod
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.PillSegmentedControl
import com.music.bitchord.ui.components.PillTextField
import com.music.bitchord.ui.icons.SfIcons
import com.music.bitchord.ui.scrobble.stats.Artwork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Where a mode sits on the page. The first group is drawn as tiles, the rest as rows. */
internal enum class GenerateGroup(@param:StringRes val label: Int) {
    LISTENING(R.string.scrobble_gen_group_lastfm),
    DISCOVER(R.string.scrobble_gen_group_discover),
    MOOD(R.string.scrobble_gen_group_mood),
    LIBRARY(R.string.scrobble_gen_group_library),
}

/** What the page shows for a mode, and what it needs before it can run. */
@Immutable
internal class GenerateModeInfo(
    val icon: ImageVector,
    @param:StringRes val title: Int,
    @param:StringRes val promise: Int,
    val group: GenerateGroup,
    val needsLastFm: Boolean = false,
    val needsYouTubeMusic: Boolean = false,
    val hasPeriod: Boolean = false,
)

internal val PlaylistGenerationMode.info: GenerateModeInfo
    get() = when (this) {
        PlaylistGenerationMode.TOP -> GenerateModeInfo(
            SfIcons.Star, R.string.scrobble_gen_mode_top, R.string.scrobble_gen_mode_top_promise,
            GenerateGroup.LISTENING, needsLastFm = true, hasPeriod = true,
        )
        PlaylistGenerationMode.RECENT -> GenerateModeInfo(
            SfIcons.Clock, R.string.scrobble_gen_mode_recent, R.string.scrobble_gen_mode_recent_promise,
            GenerateGroup.LISTENING, needsLastFm = true,
        )
        PlaylistGenerationMode.FORGOTTEN_FAVORITES -> GenerateModeInfo(
            SfIcons.History, R.string.scrobble_gen_mode_forgotten, R.string.scrobble_mode_forgotten_favorites_promise,
            GenerateGroup.LISTENING, needsLastFm = true,
        )
        PlaylistGenerationMode.TOP_ARTISTS_MIX -> GenerateModeInfo(
            SfIcons.Artist, R.string.scrobble_gen_mode_top_artists, R.string.scrobble_mode_top_artists_mix_promise,
            GenerateGroup.LISTENING, needsLastFm = true, hasPeriod = true,
        )
        PlaylistGenerationMode.SIMILAR_TRACKS -> GenerateModeInfo(
            SfIcons.Radio, R.string.scrobble_mode_similar_tracks, R.string.scrobble_mode_similar_tracks_promise,
            GenerateGroup.DISCOVER,
        )
        PlaylistGenerationMode.SIMILAR_ARTISTS -> GenerateModeInfo(
            SfIcons.Groups, R.string.scrobble_mode_similar_artists, R.string.scrobble_mode_similar_artists_promise,
            GenerateGroup.DISCOVER,
        )
        PlaylistGenerationMode.MIX -> GenerateModeInfo(
            SfIcons.Shuffle, R.string.scrobble_gen_mode_mix, R.string.scrobble_mode_mix_promise,
            GenerateGroup.DISCOVER,
        )
        PlaylistGenerationMode.NEVER_HEARD -> GenerateModeInfo(
            SfIcons.AutoAwesome, R.string.scrobble_gen_mode_never_heard, R.string.scrobble_mode_never_heard_promise,
            GenerateGroup.DISCOVER, needsLastFm = true,
        )
        PlaylistGenerationMode.RECOMMENDATIONS -> GenerateModeInfo(
            SfIcons.Heart, R.string.scrobble_gen_mode_recommendations, R.string.scrobble_mode_recommendations_promise,
            GenerateGroup.DISCOVER,
        )
        PlaylistGenerationMode.TAG_TOP -> GenerateModeInfo(
            SfIcons.ShowChart, R.string.scrobble_gen_mode_tag_top, R.string.scrobble_mode_tag_top_promise,
            GenerateGroup.MOOD,
        )
        PlaylistGenerationMode.TAG -> GenerateModeInfo(
            SfIcons.LocalOffer, R.string.scrobble_mode_tag, R.string.scrobble_mode_tag_promise,
            GenerateGroup.MOOD,
        )
        PlaylistGenerationMode.LIBRARY -> GenerateModeInfo(
            SfIcons.LibraryMusic, R.string.scrobble_gen_mode_library, R.string.scrobble_mode_library_promise,
            GenerateGroup.LIBRARY, needsYouTubeMusic = true,
        )
    }

/** Display order inside each group. */
private val ModeOrder = listOf(
    PlaylistGenerationMode.TOP,
    PlaylistGenerationMode.RECENT,
    PlaylistGenerationMode.FORGOTTEN_FAVORITES,
    PlaylistGenerationMode.TOP_ARTISTS_MIX,
    PlaylistGenerationMode.SIMILAR_TRACKS,
    PlaylistGenerationMode.SIMILAR_ARTISTS,
    PlaylistGenerationMode.MIX,
    PlaylistGenerationMode.NEVER_HEARD,
    PlaylistGenerationMode.RECOMMENDATIONS,
    PlaylistGenerationMode.TAG_TOP,
    PlaylistGenerationMode.TAG,
    PlaylistGenerationMode.LIBRARY,
)

private val GenreChips = listOf(
    "pop", "rock", "hip-hop", "electronic", "jazz", "lofi",
    "metal", "indie", "classical", "r&b", "ambient", "punk",
)

/** The accounts a mode can lean on. */
@Immutable
internal data class GenerateAccounts(
    val lastFmConnected: Boolean,
    val lastFmUsername: String,
    val youTubeMusicSignedIn: Boolean,
)

/** Why [mode] can't run with these [accounts], or null when it can. */
@StringRes
internal fun GenerateAccounts.blockedReason(mode: PlaylistGenerationMode): Int? = when {
    mode.info.needsLastFm && !lastFmConnected -> R.string.scrobble_gen_needs_lastfm
    mode.info.needsYouTubeMusic && !youTubeMusicSignedIn -> R.string.scrobble_gen_sign_in_to_use
    else -> null
}

/** Everything the sheet asks for, for one mode. The seed track is kept only in memory. */
@Immutable
internal data class GenerateForm(
    val mode: PlaylistGenerationMode = PlaylistGenerationMode.TOP,
    val count: Int = 25,
    val period: LastFmStatsPeriod = LastFmStatsPeriod.OVERALL,
    val seedQuery: String = "",
    val seed: Song? = null,
    val artist: String = "",
    val tag: String = "",
    val excludeSaved: Boolean = false,
    val excludeLiked: Boolean = false,
    val excludeLibrary: Boolean = false,
) {
    fun toRequest() = PlaylistGenerationRequest(
        mode = mode,
        count = count,
        period = period,
        seedTrack = seed,
        seedArtist = artist,
        tag = tag,
        excludeSavedPlaylists = excludeSaved,
        excludeLiked = excludeLiked,
        excludeLibrary = excludeLibrary,
    )

    /** Whether the form has what [mode] needs; accounts are checked separately. */
    val complete: Boolean
        get() = when (mode) {
            PlaylistGenerationMode.SIMILAR_TRACKS -> seed?.let(PlaylistGenerationRules::canStartMixWithSong) == true
            PlaylistGenerationMode.SIMILAR_ARTISTS -> artist.isNotBlank()
            PlaylistGenerationMode.TAG, PlaylistGenerationMode.TAG_TOP -> tag.isNotBlank()
            else -> true
        }

    companion object {
        val Saver = listSaver<GenerateForm, Any>(
            save = {
                listOf(
                    it.mode.name, it.count, it.period.name, it.seedQuery, it.artist, it.tag,
                    it.excludeSaved, it.excludeLiked, it.excludeLibrary,
                )
            },
            restore = {
                GenerateForm(
                    mode = PlaylistGenerationMode.valueOf(it[0] as String),
                    count = it[1] as Int,
                    period = LastFmStatsPeriod.valueOf(it[2] as String),
                    seedQuery = it[3] as String,
                    artist = it[4] as String,
                    tag = it[5] as String,
                    excludeSaved = it[6] as Boolean,
                    excludeLiked = it[7] as Boolean,
                    excludeLibrary = it[8] as Boolean,
                )
            },
        )
    }
}

/** The seed search under the form: what came back, and whether it is still looking. */
@Immutable
internal data class SeedSearch(
    val results: List<Song> = emptyList(),
    val searching: Boolean = false,
    val failed: Boolean = false,
)

/**
 * The Generate tab: the ways to build a playlist, grouped by where the music
 * comes from, with Last.fm's first. A tap opens a sheet that asks only for
 * what that mode needs; the result lands in Playlists.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScrobbleGenerateContent(
    viewModel: ScrobbleGenerateViewModel,
    currentSong: Song?,
    contentPadding: PaddingValues,
    highlightedPlaylistId: String?,
    sharedHandoffFromGenerate: Boolean,
    reduceMotion: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onOpenPlaylists: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ytmSignedIn by SignedIn.state(context).collectAsStateWithLifecycle()
    val lastFmEnabled by AppSettings.lastfmEnabled.collectAsStateWithLifecycle()
    val lastFmUsername by AppSettings.lastfmUsername.collectAsStateWithLifecycle()
    val accounts = GenerateAccounts(
        lastFmConnected = lastFmEnabled && lastFmUsername.isNotBlank() && LastFM.isInitialized(),
        lastFmUsername = lastFmUsername.trim(),
        youTubeMusicSignedIn = ytmSignedIn,
    )

    var form by rememberSaveable(stateSaver = GenerateForm.Saver) { mutableStateOf(GenerateForm()) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var search by remember { mutableStateOf(SeedSearch()) }

    val seeded = form.mode == PlaylistGenerationMode.SIMILAR_TRACKS || form.mode == PlaylistGenerationMode.MIX
    LaunchedEffect(form.seedQuery, form.mode, sheetOpen) {
        val query = form.seedQuery.trim()
        if (!sheetOpen || !seeded || query.length < 2) {
            search = SeedSearch()
            return@LaunchedEffect
        }
        delay(300)
        search = search.copy(searching = true, failed = false)
        search = try {
            val results = YtMusicRepository.search(query, SearchFilter.SONGS).getOrThrow()
            currentCoroutineContext().ensureActive()
            SeedSearch(
                results = results.mapNotNull { result ->
                    when (result) {
                        is SearchResult.TopTrack -> result.song
                        is SearchResult.Track -> result.song
                        is SearchResult.Browse -> null
                    }
                }.filter(PlaylistGenerationRules::canStartMixWithSong).distinctBy { it.videoId }.take(6),
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            SeedSearch(failed = true)
        }
    }

    val savedModifier = uiState.savedPlaylist?.let { playlist ->
        if (playlist.id == highlightedPlaylistId && sharedHandoffFromGenerate && !reduceMotion) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    rememberSharedContentState(key = "scrobble-playlist-" + playlist.id),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        } else {
            null
        }
    } ?: Modifier

    GenerateScreen(
        uiState = uiState,
        accounts = accounts,
        contentPadding = contentPadding,
        showStatus = !sheetOpen,
        savedModifier = savedModifier,
        onOpenMode = { mode ->
            if (!uiState.isGenerating && accounts.blockedReason(mode) == null) {
                form = GenerateForm(mode = mode)
                search = SeedSearch()
                viewModel.clearResult()
                sheetOpen = true
            }
        },
        onCancel = viewModel::cancelGeneration,
        onRetry = { uiState.activeRequest?.let(viewModel::generate) },
        onDismissError = viewModel::dismissError,
        onOpenPlaylists = onOpenPlaylists,
        onConnectLastFm = onOpenSettings,
    )

    if (sheetOpen) {
        val generating = rememberUpdatedState(uiState.isGenerating)
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target -> !generating.value || target != SheetValue.Hidden },
        )
        // A finished run moves the page on to Playlists; the sheet has done its job.
        LaunchedEffect(uiState.savedPlaylist?.id) {
            if (uiState.savedPlaylist != null) sheetOpen = false
        }
        ModalBottomSheet(
            onDismissRequest = { if (!uiState.isGenerating) sheetOpen = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            dragHandle = { SheetHandle() },
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        ) {
            GenerateSheetContent(
                form = form,
                onFormChange = { form = it },
                search = search,
                currentSong = currentSong,
                uiState = uiState,
                onGenerate = { viewModel.generate(form.toRequest()) },
                onCancel = {
                    if (uiState.isGenerating) viewModel.cancelGeneration() else sheetOpen = false
                },
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(width = 36.dp, height = 5.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
    )
}

/**
 * The page itself, stateless so a test can draw any state of it. [showStatus]
 * is false while the sheet is up, which shows the same progress itself.
 */
@Composable
internal fun GenerateScreen(
    uiState: ScrobbleGenerateUiState,
    accounts: GenerateAccounts,
    contentPadding: PaddingValues,
    onOpenMode: (PlaylistGenerationMode) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onConnectLastFm: () -> Unit,
    showStatus: Boolean = true,
    savedModifier: Modifier = Modifier,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentPadding.calculateBottomPadding() + 32.dp),
        ) {
            ScrobblePageTitle(
                title = stringResource(R.string.scrobble_tab_generate),
                subtitle = if (accounts.lastFmConnected) {
                    stringResource(R.string.scrobble_gen_subtitle_account, accounts.lastFmUsername)
                } else {
                    stringResource(R.string.scrobble_gen_subtitle)
                },
                topPadding = contentPadding.calculateTopPadding(),
            )

            Column(
                Modifier.padding(horizontal = PAGE_GUTTER).padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GenerateStatus(
                    uiState = uiState,
                    visible = showStatus,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onDismissError = onDismissError,
                    onOpenPlaylists = onOpenPlaylists,
                    savedModifier = savedModifier,
                )
                if (!accounts.lastFmConnected) ConnectLastFmCard(onConnectLastFm)
            }

            GenerateGroup.entries.forEach { group ->
                val modes = ModeOrder.filter { it.info.group == group }
                Column(Modifier.padding(horizontal = PAGE_GUTTER).padding(top = 26.dp)) {
                    ScrobbleSectionLabel(stringResource(group.label))
                    if (group == GenerateGroup.LISTENING) {
                        ModeTiles(modes, accounts, uiState.isGenerating, onOpenMode)
                    } else {
                        Column(Modifier.fillMaxWidth().scrobbleCard(RoundedCornerShape(20.dp))) {
                            modes.forEachIndexed { index, mode ->
                                if (index > 0) ScrobbleHairline(start = 56.dp)
                                ModeRow(
                                    mode = mode,
                                    blocked = accounts.blockedReason(mode),
                                    busy = uiState.isGenerating,
                                    onClick = { onOpenMode(mode) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Progress, a failure, or the saved playlist — whichever the last run left. */
@Composable
private fun GenerateStatus(
    uiState: ScrobbleGenerateUiState,
    visible: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onOpenPlaylists: () -> Unit,
    savedModifier: Modifier,
) {
    val generating = visible && uiState.isGenerating
    val error = uiState.error.takeIf { visible && !uiState.isGenerating }
    val saved = uiState.savedPlaylist.takeIf { !uiState.isGenerating }
    AnimatedVisibility(
        visible = generating,
        enter = fadeIn(tween(180)) + expandVertically(tween(220)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
    ) {
        ScrobbleStatusCard(
            title = stringResource(R.string.scrobble_building_mix),
            body = uiState.progress.ifBlank { stringResource(R.string.scrobble_gen_loading_fallback) },
            busy = true,
            action = stringResource(R.string.cancel),
            onAction = onCancel,
        )
    }
    AnimatedVisibility(
        visible = error != null,
        enter = fadeIn(tween(180)) + expandVertically(tween(220)),
        exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
    ) {
        ScrobbleStatusCard(
            title = stringResource(R.string.scrobble_gen_failed_title),
            body = error,
            icon = SfIcons.ErrorOutline,
            iconTint = MaterialTheme.colorScheme.error,
            action = stringResource(if (uiState.activeRequest != null) R.string.retry else R.string.scrobble_dismiss),
            onAction = if (uiState.activeRequest != null) onRetry else onDismissError,
        )
    }
    if (saved != null) SavedPlaylistCard(saved, onOpenPlaylists, savedModifier)
}

@Composable
private fun SavedPlaylistCard(playlist: LocalPlaylist, onOpen: () -> Unit, modifier: Modifier) {
    ScrobbleStatusCard(
        title = playlist.title,
        body = stringResource(R.string.scrobble_gen_saved_body, playlist.songs.size),
        modifier = modifier,
        action = stringResource(R.string.open),
        onAction = onOpen,
        leading = {
            Artwork(
                url = playlist.customCoverUri ?: playlist.songs.firstOrNull()?.thumbnailUrl,
                icon = SfIcons.PlaylistPlay,
                size = 44.dp,
                shape = RoundedCornerShape(8.dp),
            )
        },
    )
}

@Composable
private fun ConnectLastFmCard(onConnect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .scrobbleCard()
            .padding(20.dp),
    ) {
        Icon(SfIcons.BarChart, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.scrobble_gen_connect_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.scrobble_gen_connect_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        ScrobblePrimaryButton(
            label = stringResource(R.string.scrobble_open_settings),
            onClick = onConnect,
            compact = true,
        )
    }
}

/** Last.fm's four modes as a two-by-two of tiles: the page's main event. */
@Composable
private fun ModeTiles(
    modes: List<PlaylistGenerationMode>,
    accounts: GenerateAccounts,
    busy: Boolean,
    onOpenMode: (PlaylistGenerationMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        modes.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { mode ->
                    ModeTile(
                        mode = mode,
                        blocked = accounts.blockedReason(mode) != null,
                        busy = busy,
                        onClick = { onOpenMode(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeTile(
    mode: PlaylistGenerationMode,
    blocked: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val info = mode.info
    val interaction = remember { MutableInteractionSource() }
    val scale = pressScale(interaction)
    val enabled = !blocked && !busy
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .heightIn(min = 148.dp)
            .scrobbleCard(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (blocked) 0.45f else 1f)
            .padding(16.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colors.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(info.icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.weight(1f).heightIn(min = 18.dp))
        Text(
            stringResource(info.title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(info.promise),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ModeRow(
    mode: PlaylistGenerationMode,
    @StringRes blocked: Int?,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val info = mode.info
    val enabled = blocked == null && !busy
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (blocked != null) 0.45f else 1f)
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(info.icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(info.title),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(blocked ?: info.promise),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(SfIcons.ChevronRight, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(13.dp))
    }
}

/**
 * What the sheet holds for one mode: its name and promise, the few inputs it
 * needs, the size, and at the bottom the progress or the button.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GenerateSheetContent(
    form: GenerateForm,
    onFormChange: (GenerateForm) -> Unit,
    search: SeedSearch,
    currentSong: Song?,
    uiState: ScrobbleGenerateUiState,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val info = form.mode.info
    val busy = uiState.isGenerating
    Column(modifier.fillMaxWidth().heightIn(max = 760.dp)) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PAGE_GUTTER + 4.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.padding(top = 12.dp)) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(info.icon, contentDescription = null, tint = colors.onSurface, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(info.title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(info.promise),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }

            if (info.hasPeriod) {
                Column {
                    ScrobbleSectionLabel(stringResource(R.string.scrobble_gen_field_period))
                    val periods = LastFmStatsPeriod.entries
                    PillSegmentedControl(
                        options = periods.map { stringResource(it.shortLabel) },
                        selectedIndex = periods.indexOf(form.period),
                        onSelect = { onFormChange(form.copy(period = periods[it])) },
                        enabled = !busy,
                    )
                }
            }

            when (form.mode) {
                PlaylistGenerationMode.SIMILAR_TRACKS,
                PlaylistGenerationMode.MIX -> SeedPicker(
                    optional = form.mode == PlaylistGenerationMode.MIX,
                    form = form,
                    onFormChange = onFormChange,
                    search = search,
                    currentSong = currentSong,
                    enabled = !busy,
                )

                PlaylistGenerationMode.SIMILAR_ARTISTS -> Column {
                    ScrobbleSectionLabel(stringResource(R.string.scrobble_gen_label_artist))
                    PillTextField(
                        value = form.artist,
                        onValueChange = { onFormChange(form.copy(artist = it)) },
                        placeholder = stringResource(R.string.scrobble_gen_field_artist),
                        enabled = !busy,
                    )
                }

                PlaylistGenerationMode.TAG,
                PlaylistGenerationMode.TAG_TOP -> Column {
                    ScrobbleSectionLabel(stringResource(R.string.scrobble_gen_field_mood))
                    PillTextField(
                        value = form.tag,
                        onValueChange = { onFormChange(form.copy(tag = it)) },
                        placeholder = stringResource(R.string.scrobble_gen_placeholder_mood),
                        enabled = !busy,
                    )
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GenreChips.forEach { genre ->
                            GenreChip(
                                label = genre,
                                selected = form.tag.trim().equals(genre, ignoreCase = true),
                                enabled = !busy,
                                onClick = { onFormChange(form.copy(tag = genre)) },
                            )
                        }
                    }
                }

                PlaylistGenerationMode.NEVER_HEARD -> ExclusionsCard(form, onFormChange, enabled = !busy)

                else -> Unit
            }

            SizeStepper(
                count = form.count,
                enabled = !busy,
                onChange = { onFormChange(form.copy(count = it)) },
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = PAGE_GUTTER + 4.dp)
                .padding(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (busy) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        color = colors.onSurface,
                        trackColor = colors.onSurface.copy(alpha = 0.12f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        uiState.progress.ifBlank { stringResource(R.string.scrobble_gen_loading_fallback) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (uiState.error != null) {
                Text(
                    uiState.error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.error,
                )
            }
            val retry = uiState.error != null && uiState.activeRequest == form.toRequest()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ScrobbleSecondaryButton(
                    label = stringResource(R.string.cancel),
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                )
                ScrobblePrimaryButton(
                    label = stringResource(if (retry) R.string.retry else R.string.scrobble_generate_button),
                    onClick = onGenerate,
                    enabled = form.complete,
                    busy = busy,
                    modifier = Modifier.weight(1.6f),
                )
            }
        }
    }
}

@Composable
private fun GenreChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(
        if (selected) colors.onSurface else colors.surfaceVariant,
        tween(160),
        label = "genre fill",
    )
    val ink by animateColorAsState(
        if (selected) colors.surface else colors.onSurface,
        tween(160),
        label = "genre ink",
    )
    Box(
        Modifier
            .heightIn(min = 36.dp)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ink)
    }
}

@Composable
private fun SeedPicker(
    optional: Boolean,
    form: GenerateForm,
    onFormChange: (GenerateForm) -> Unit,
    search: SeedSearch,
    currentSong: Song?,
    enabled: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Column {
        ScrobbleSectionLabel(
            stringResource(if (optional) R.string.scrobble_gen_field_optional_seed else R.string.scrobble_gen_field_seed_track),
        )
        val playing = currentSong?.takeIf(PlaylistGenerationRules::canStartMixWithSong)
        val candidates = buildList {
            if (playing != null) add(playing)
            form.seed?.takeIf { seed -> none { it.videoId == seed.videoId } }?.let(::add)
            search.results.filterTo(this) { result -> none { it.videoId == result.videoId } }
        }
        PillTextField(
            value = form.seedQuery,
            onValueChange = { onFormChange(form.copy(seedQuery = it)) },
            placeholder = stringResource(R.string.scrobble_gen_search_placeholder),
            enabled = enabled,
        )
        if (search.searching || search.failed || (form.seedQuery.trim().length >= 2 && search.results.isEmpty())) {
            Text(
                text = when {
                    search.searching -> stringResource(R.string.scrobble_gen_searching)
                    search.failed -> stringResource(R.string.scrobble_gen_error_search)
                    else -> stringResource(R.string.scrobble_no_tracks_found)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (search.failed) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
        if (candidates.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.fillMaxWidth().scrobbleCard(RoundedCornerShape(18.dp))) {
                candidates.forEachIndexed { index, song ->
                    if (index > 0) ScrobbleHairline(start = 72.dp)
                    val selected = form.seed?.videoId == song.videoId
                    SeedRow(
                        song = song,
                        caption = if (song.videoId == playing?.videoId) stringResource(R.string.scrobble_gen_now_playing) else null,
                        selected = selected,
                        enabled = enabled,
                        onClick = {
                            onFormChange(form.copy(seed = if (selected && optional) null else song))
                        },
                    )
                }
            }
        }
        if (optional && form.seed == null) {
            Text(
                stringResource(R.string.scrobble_gen_seed_none),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun SeedRow(song: Song, caption: String?, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val state = stringResource(if (selected) R.string.selected else R.string.scrobble_gen_not_selected)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { stateDescription = state },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(url = song.thumbnailUrl, icon = SfIcons.MusicNote, size = 48.dp, shape = RoundedCornerShape(8.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (caption != null) {
                Text(
                    caption.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(song.title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) colors.onSurface else colors.onSurface.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) Icon(SfIcons.Check, contentDescription = null, tint = colors.surface, modifier = Modifier.size(11.dp))
        }
    }
}

@Composable
private fun ExclusionsCard(form: GenerateForm, onFormChange: (GenerateForm) -> Unit, enabled: Boolean) {
    ScrobbleGroup(
        label = stringResource(R.string.scrobble_gen_section_exclusions),
        footer = stringResource(R.string.scrobble_gen_exclude_hint),
    ) {
        ToggleRow(stringResource(R.string.scrobble_gen_exclude_saved), form.excludeSaved, enabled) {
            onFormChange(form.copy(excludeSaved = it))
        }
        ScrobbleHairline(start = 16.dp)
        ToggleRow(stringResource(R.string.scrobble_gen_exclude_liked), form.excludeLiked, enabled) {
            onFormChange(form.copy(excludeLiked = it))
        }
        ScrobbleHairline(start = 16.dp)
        ToggleRow(stringResource(R.string.scrobble_gen_exclude_library), form.excludeLibrary, enabled) {
            onFormChange(form.copy(excludeLibrary = it))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .heightIn(min = 52.dp)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.surface,
                checkedTrackColor = colors.onSurface,
                uncheckedThumbColor = colors.onSurfaceVariant,
                uncheckedTrackColor = colors.onSurface.copy(alpha = 0.1f),
                uncheckedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
    }
}

/** The playlist's size: a minus, the count, a plus, in fives. */
@Composable
private fun SizeStepper(count: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val min = PlaylistGenerationRepository.MIN_TRACKS
    val max = PlaylistGenerationRepository.MAX_TRACKS
    Column {
        ScrobbleSectionLabel(stringResource(R.string.scrobble_gen_section_size))
        Row(
            Modifier
                .fillMaxWidth()
                .scrobbleCard(RoundedCornerShape(18.dp))
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.scrobble_tracks_count, count),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                )
                Text(
                    stringResource(R.string.scrobble_gen_size_range, min, max),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            ScrobbleIconButton(
                icon = SfIcons.Minus,
                description = stringResource(R.string.scrobble_gen_fewer),
                onClick = { onChange((count - 5).coerceAtLeast(min)) },
                enabled = enabled && count > min,
            )
            Text(
                "$count",
                style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp),
            )
            ScrobbleIconButton(
                icon = SfIcons.Add,
                description = stringResource(R.string.scrobble_gen_more),
                onClick = { onChange((count + 5).coerceAtMost(max)) },
                enabled = enabled && count < max,
            )
        }
    }
}

/** The short tag a saved playlist wears for the mode it came from, or null for one made by hand. */
internal fun playlistGenerationModeLabel(storageValue: String?): String? = when (storageValue) {
    PlaylistGenerationMode.TOP.storageValue -> "Top"
    PlaylistGenerationMode.RECENT.storageValue -> "Recent"
    PlaylistGenerationMode.SIMILAR_TRACKS.storageValue -> "Similar Tracks"
    PlaylistGenerationMode.SIMILAR_ARTISTS.storageValue -> "Similar Artists"
    PlaylistGenerationMode.TAG.storageValue -> "Tag"
    PlaylistGenerationMode.MIX.storageValue -> "Mix"
    PlaylistGenerationMode.RECOMMENDATIONS.storageValue -> "Recommendations"
    PlaylistGenerationMode.NEVER_HEARD.storageValue -> "Never Heard"
    PlaylistGenerationMode.LIBRARY.storageValue -> "Library"
    PlaylistGenerationMode.FORGOTTEN_FAVORITES.storageValue -> "Forgotten Favorites"
    PlaylistGenerationMode.TAG_TOP.storageValue -> "Tag Top Tracks"
    PlaylistGenerationMode.TOP_ARTISTS_MIX.storageValue -> "Top Artists Mix"
    "similar-playlist" -> "Similar Playlist"
    else -> null
}

/** The same tag, localised, for the screen. */
@StringRes
internal fun playlistGenerationModeLabelRes(storageValue: String?): Int? =
    PlaylistGenerationMode.entries.firstOrNull { it.storageValue == storageValue }?.info?.title
        ?: if (storageValue == "similar-playlist") R.string.scrobble_mode_short_similar_playlist else null

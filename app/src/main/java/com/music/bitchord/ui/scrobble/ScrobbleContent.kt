package com.music.bitchord.ui.scrobble

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.music.bitchord.ui.screens.LocalPlaylistReleaseHeader
import com.music.bitchord.ui.components.SongRow
import com.music.bitchord.ui.components.AlertAction
import com.music.bitchord.ui.components.AlertRule
import com.music.bitchord.ui.components.AlertScaffold
import com.music.bitchord.ui.components.PillTextField
import com.music.bitchord.ui.components.SearchField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.res.stringResource
import com.music.bitchord.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.music.bitchord.auth.SignedIn
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.generate.PlaylistGenerationMode
import com.music.bitchord.data.generate.PlaylistGenerationRepository
import com.music.bitchord.data.generate.PlaylistGenerationRules
import com.music.bitchord.data.generate.PlaylistGenerationRequest
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.data.spotify.LocalPlaylistSortMode
import com.music.bitchord.data.spotify.sortLocalPlaylists
import com.music.bitchord.data.stats.ReplayPeriod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

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
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ytmSignedIn by SignedIn.state(context).collectAsStateWithLifecycle()
    val lastFmConnected by AppSettings.lastfmEnabled.collectAsStateWithLifecycle()

    var mode by rememberSaveable { mutableStateOf(PlaylistGenerationMode.TOP) }
    var showGeneratorDialog by rememberSaveable { mutableStateOf(false) }
    var count by rememberSaveable { mutableStateOf(25) }
    var period by rememberSaveable { mutableStateOf(ReplayPeriod.ALL_TIME) }
    var seedQuery by rememberSaveable { mutableStateOf("") }
    var selectedSeed by remember { mutableStateOf<Song?>(null) }
    var artist by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf("") }
    var excludeSaved by rememberSaveable { mutableStateOf(false) }
    var excludeLiked by rememberSaveable { mutableStateOf(false) }
    var excludeLibrary by rememberSaveable { mutableStateOf(false) }
    var seedResults by remember { mutableStateOf<List<Song>>(emptyList()) }
    var seedSearching by remember { mutableStateOf(false) }
    var seedSearchError by remember { mutableStateOf<String?>(null) }

    val isGeneratingState = rememberUpdatedState(uiState.isGenerating)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            !isGeneratingState.value || target != SheetValue.Hidden
        },
    )

    LaunchedEffect(seedQuery, mode, showGeneratorDialog) {
        if (
            !showGeneratorDialog ||
            (mode != PlaylistGenerationMode.SIMILAR_TRACKS && mode != PlaylistGenerationMode.MIX) ||
            seedQuery.trim().length < 2
        ) {
            seedResults = emptyList()
            seedSearchError = null
            seedSearching = false
            return@LaunchedEffect
        }
        delay(300)
        seedSearching = true
        seedSearchError = null
        try {
            val response = YtMusicRepository.search(seedQuery.trim(), SearchFilter.SONGS).getOrThrow()
            currentCoroutineContext().ensureActive()
            seedResults = response.mapNotNull { result ->
                when (result) {
                    is SearchResult.TopTrack -> result.song
                    is SearchResult.Track -> result.song
                    is SearchResult.Browse -> null
                }
            }.filter { PlaylistGenerationRules.canStartMixWithSong(it) }
                .distinctBy { it.videoId }
                .take(6)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            seedResults = emptyList()
            seedSearchError = error.message ?: "Couldn't search YouTube Music"
        } finally {
            seedSearching = false
        }
    }

    val generationRequest = PlaylistGenerationRequest(
        mode = mode,
        count = count,
        period = period,
        seedTrack = selectedSeed,
        seedArtist = artist,
        tag = tag,
        excludeSavedPlaylists = excludeSaved,
        excludeLiked = excludeLiked,
        excludeLibrary = excludeLibrary,
    )
    val retryingCurrentRequest = uiState.error != null && uiState.activeRequest == generationRequest

    val canGenerate = !uiState.isGenerating &&
        (mode != PlaylistGenerationMode.LIBRARY || ytmSignedIn) &&
        when (mode) {
            PlaylistGenerationMode.SIMILAR_TRACKS -> selectedSeed?.let { PlaylistGenerationRules.canStartMixWithSong(it) } == true
            PlaylistGenerationMode.SIMILAR_ARTISTS -> artist.isNotBlank()
            // Tag deliberately reaches repository validation so an empty value
            // produces an actionable validation error instead of a dead button.
            else -> true
        }

    val openMode: (PlaylistGenerationMode) -> Unit = { option ->
        if (!uiState.isGenerating && (option != PlaylistGenerationMode.LIBRARY || ytmSignedIn)) {
            mode = option
            count = 25
            period = ReplayPeriod.ALL_TIME
            seedQuery = ""
            selectedSeed = null
            artist = ""
            tag = ""
            excludeSaved = false
            excludeLiked = false
            excludeLibrary = false
            seedResults = emptyList()
            seedSearchError = null
            viewModel.clearResult()
            showGeneratorDialog = true
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.scrobble_tab_generate), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
                Text(
                    "Pick a vibe to build a playlist.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GeneratorSectionTitle("Pick a mood")
                Column {
                    Box(
                        Modifier.fillMaxWidth().height(0.5.dp)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                    )
                    PlaylistGenerationMode.entries.forEach { option ->
                        GeneratorModeRow(
                            mode = option,
                            enabled = !uiState.isGenerating && (option != PlaylistGenerationMode.LIBRARY || ytmSignedIn),
                            disabledReason = if (option == PlaylistGenerationMode.LIBRARY && !ytmSignedIn) {
                                "Sign in to YouTube Music to use this"
                            } else {
                                null
                            },
                            onClick = { openMode(option) },
                        )
                        Box(
                            Modifier.fillMaxWidth().height(0.5.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
                        )
                    }
                }
            }

            if (uiState.isGenerating && !showGeneratorDialog) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.scrobble_building_mix), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = viewModel::cancelGeneration, colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSecondaryContainer)) { Text(stringResource(R.string.cancel)) }
                        }
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = scrobbleAccentColor())
                        Text(
                            uiState.progress.ifBlank { "Finding tracks that fit…" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!showGeneratorDialog) {
                uiState.error?.let { error ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(error, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                            val retryRequest = uiState.activeRequest
                            if (retryRequest != null) {
                                TextButton(onClick = { viewModel.generate(retryRequest) }, colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onErrorContainer)) { Text(stringResource(R.string.retry)) }
                            } else {
                                TextButton(onClick = viewModel::dismissError, colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onErrorContainer)) { Text(stringResource(R.string.scrobble_dismiss)) }
                            }
                        }
                    }
                }
            }

            uiState.savedPlaylist?.let { playlist ->
                val sharedBoundsModifier = if (
                    playlist.id == highlightedPlaylistId && sharedHandoffFromGenerate && !reduceMotion
                ) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = "scrobble-playlist-" + playlist.id),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                } else {
                    Modifier
                }
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.fillMaxWidth().then(sharedBoundsModifier),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AsyncImage(
                            model = playlist.customCoverUri ?: playlist.songs.firstOrNull()?.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.scrobble_playlist_saved), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                playlist.title,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text("${playlist.songs.size} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = onOpenPlaylists, colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSecondaryContainer)) { Text(stringResource(R.string.open)) }
                    }
                }
            }
        }
    }

    if (showGeneratorDialog) {
        ModalBottomSheet(
            onDismissRequest = { if (!uiState.isGenerating) showGeneratorDialog = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 1f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(mode.label(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(mode.description(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (!lastFmConnected) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            "Works without Last.fm. Connect it for better picks.",
                            modifier = Modifier.fillMaxWidth().padding(13.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (mode == PlaylistGenerationMode.TOP || mode == PlaylistGenerationMode.LIBRARY) {
                    GeneratorFieldLabel("Period")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        ReplayPeriod.entries.forEach { option ->
                            FilterChip(
                                selected = period == option,
                                onClick = { period = option },
                                enabled = !uiState.isGenerating &&
                                    (mode != PlaylistGenerationMode.LIBRARY || option == ReplayPeriod.ALL_TIME),
                                modifier = Modifier.heightIn(min = 48.dp),
                                label = { Text(option.label()) },
                            )
                        }
                    }
                    if (mode == PlaylistGenerationMode.LIBRARY) {
                        Text(
                            "YouTube Music library tracks don't include a saved date in Mylo yet, so only All time can be applied.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                when (mode) {
                    PlaylistGenerationMode.TOP,
                    PlaylistGenerationMode.RECENT,
                    PlaylistGenerationMode.RECOMMENDATIONS,
                    PlaylistGenerationMode.LIBRARY -> Unit

                    PlaylistGenerationMode.SIMILAR_TRACKS -> {
                        GeneratorSeedPicker(
                            label = "Seed track",
                            currentSong = currentSong,
                            selectedSeed = selectedSeed,
                            seedQuery = seedQuery,
                            seedResults = seedResults,
                            seedSearching = seedSearching,
                            seedSearchError = seedSearchError,
                            enabled = !uiState.isGenerating,
                            allowOptional = false,
                            onQueryChange = {
                                seedQuery = it
                                selectedSeed = null
                                seedResults = emptyList()
                                seedSearchError = null
                            },
                            onSelectSeed = { selectedSeed = it },
                        )
                    }

                    PlaylistGenerationMode.SIMILAR_ARTISTS -> {
                        GeneratorFieldLabel("Artist")
                        PillTextField(
                            value = artist,
                            onValueChange = { artist = it },
                            placeholder = "Artist name",
                            enabled = !uiState.isGenerating,
                        )
                    }

                    PlaylistGenerationMode.TAG -> {
                        GeneratorFieldLabel("Mood or genre")
                        PillTextField(
                            value = tag,
                            onValueChange = { tag = it },
                            placeholder = "indie, late-night jazz, ambient…",
                            enabled = !uiState.isGenerating,
                        )
                        val genres = listOf("pop", "rock", "hip-hop", "electronic", "jazz", "lofi", "metal", "indie", "classical", "r&b", "ambient", "punk")
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            genres.forEach { genre ->
                                FilterChip(
                                    selected = tag.equals(genre, ignoreCase = true),
                                    onClick = { tag = genre },
                                    enabled = !uiState.isGenerating,
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    label = { Text(genre) },
                                )
                            }
                        }
                    }

                    PlaylistGenerationMode.MIX -> {
                        GeneratorSeedPicker(
                            label = "Optional seed",
                            currentSong = currentSong,
                            selectedSeed = selectedSeed,
                            seedQuery = seedQuery,
                            seedResults = seedResults,
                            seedSearching = seedSearching,
                            seedSearchError = seedSearchError,
                            enabled = !uiState.isGenerating,
                            allowOptional = true,
                            onQueryChange = {
                                seedQuery = it
                                selectedSeed = null
                                seedResults = emptyList()
                                seedSearchError = null
                            },
                            onSelectSeed = { selectedSeed = it },
                        )
                    }

                    PlaylistGenerationMode.NEVER_HEARD -> {
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            GeneratorFieldLabel("Optional exclusions")
                            GeneratorExclusionToggle("Tracks in saved playlists", excludeSaved, enabled = true) { excludeSaved = it }
                            GeneratorExclusionToggle("Liked tracks", excludeLiked, enabled = ytmSignedIn) { excludeLiked = it }
                            GeneratorExclusionToggle("Library tracks", excludeLibrary, enabled = ytmSignedIn) { excludeLibrary = it }
                            if (!ytmSignedIn) {
                                Text(
                                    "Sign in to YouTube Music to exclude liked or library tracks.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "Only tracks you've actually heard are excluded by default. Add these filters if you want a stricter discovery mix.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    GeneratorFieldLabel("Playlist size")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { count = (count - 5).coerceAtLeast(PlaylistGenerationRepository.MIN_TRACKS) },
                            enabled = !uiState.isGenerating && count > PlaylistGenerationRepository.MIN_TRACKS,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Text("−", style = MaterialTheme.typography.titleLarge)
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                count.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                color = scrobbleAccentColor(),
                                fontWeight = FontWeight.Bold,
                            )
                            Text(stringResource(R.string.scrobble_tracks_steps), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(
                            onClick = { count = (count + 5).coerceAtMost(PlaylistGenerationRepository.MAX_TRACKS) },
                            enabled = !uiState.isGenerating && count < PlaylistGenerationRepository.MAX_TRACKS,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Text("+", style = MaterialTheme.typography.titleLarge)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${PlaylistGenerationRepository.MIN_TRACKS} tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${PlaylistGenerationRepository.MAX_TRACKS} tracks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (uiState.isGenerating) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = scrobbleAccentColor())
                    Text(
                        uiState.progress.ifBlank { "Finding tracks that fit…" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                uiState.error?.let { error ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                            Text(error, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            if (uiState.isGenerating) {
                                viewModel.cancelGeneration()
                            } else {
                                showGeneratorDialog = false
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSurface),
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Button(
                        onClick = {
                            viewModel.generate(generationRequest)
                        },
                        colors = scrobblePrimaryButtonColors(),
                        enabled = canGenerate,
                        modifier = Modifier.weight(1.45f).height(52.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Text(
                            when {
                                uiState.isGenerating -> "Building your mix…"
                                retryingCurrentRequest -> "Retry"
                                else -> "Generate playlist"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratorSeedPicker(
    label: String,
    currentSong: Song?,
    selectedSeed: Song?,
    seedQuery: String,
    seedResults: List<Song>,
    seedSearching: Boolean,
    seedSearchError: String?,
    enabled: Boolean,
    allowOptional: Boolean,
    onQueryChange: (String) -> Unit,
    onSelectSeed: (Song?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        GeneratorFieldLabel(label)
        currentSong?.takeIf { PlaylistGenerationRules.canStartMixWithSong(it) }?.let { playing ->
            OutlinedButton(
                onClick = {
                    val alreadySelected = selectedSeed?.videoId == playing.videoId
                    onSelectSeed(if (allowOptional && alreadySelected) null else playing)
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = scrobbleOutlinedButtonColors(MaterialTheme.colorScheme.onSurface),
            ) {
                Text(
                    when {
                        allowOptional && selectedSeed?.videoId == playing.videoId -> "Remove now playing seed · " + playing.title
                        allowOptional -> "Add now playing as a seed · " + playing.title
                        selectedSeed?.videoId == playing.videoId -> "Using now playing · " + playing.title
                        else -> "Use now playing · " + playing.title
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PillTextField(
            value = seedQuery,
            onValueChange = onQueryChange,
            placeholder = "Search for a track",
            enabled = enabled,
        )
        if (allowOptional && selectedSeed == null) {
            Text(
                "No seed selected. Mylo will build a mix from your listening and recommendations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (seedSearching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = scrobbleAccentColor())
        seedSearchError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (!seedSearching && seedSearchError == null && seedQuery.trim().length >= 2 && seedResults.isEmpty()) {
            Text(stringResource(R.string.scrobble_no_tracks_found), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        seedResults.forEach { song ->
            GeneratorSeedResult(
                song = song,
                selected = selectedSeed?.videoId == song.videoId,
                enabled = enabled,
                onClick = { onSelectSeed(song) },
            )
        }
        selectedSeed?.let {
            Text(
                "Selected · " + it.title + " — " + it.artist,
                style = MaterialTheme.typography.labelLarge,
                color = scrobbleAccentColor(),
            )
        }
    }
}
@Composable
private fun GeneratorSeedResult(
    song: Song,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AsyncImage(
                model = song.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = scrobbleAccentColor())
        }
    }
}

@Composable
private fun GeneratorExclusionToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = scrobbleCheckboxColors(),
        )
        Text(
            label,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        )
    }
}

@Composable
private fun GeneratorSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
}

@Composable
private fun GeneratorFieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun GeneratorModeRow(
    mode: PlaylistGenerationMode,
    enabled: Boolean,
    disabledReason: String?,
    onClick: () -> Unit,
) {
    val icon = when (mode) {
        PlaylistGenerationMode.TOP -> Icons.Rounded.Favorite
        PlaylistGenerationMode.RECENT -> Icons.Rounded.Schedule
        PlaylistGenerationMode.SIMILAR_TRACKS -> Icons.Rounded.MusicNote
        PlaylistGenerationMode.SIMILAR_ARTISTS -> Icons.Rounded.PeopleAlt
        PlaylistGenerationMode.TAG -> Icons.Rounded.LocalOffer
        PlaylistGenerationMode.MIX -> Icons.Rounded.Shuffle
        PlaylistGenerationMode.RECOMMENDATIONS -> Icons.Rounded.AutoAwesome
        PlaylistGenerationMode.NEVER_HEARD -> Icons.Rounded.Explore
        PlaylistGenerationMode.LIBRARY -> Icons.Rounded.LibraryMusic
    }
    val gradientEnabled = LocalScrobbleGradientEnabled.current
    val iconTileBrush = if (gradientEnabled) {
        Brush.linearGradient(listOf(Color(0xFF4A3D6B), Color(0xFFB9A7E8)))
    } else {
        Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer),
        )
    }
    androidx.compose.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f),
    ) {
        Row(
            Modifier.heightIn(min = 64.dp).padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(iconTileBrush, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (gradientEnabled) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    mode.label(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    disabledReason ?: mode.description().removeSuffix("."),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

internal fun playlistGenerationModeLabel(storageValue: String?): String? = when (storageValue) {
    PlaylistGenerationMode.TOP.storageValue -> PlaylistGenerationMode.TOP.label()
    PlaylistGenerationMode.RECENT.storageValue -> PlaylistGenerationMode.RECENT.label()
    PlaylistGenerationMode.SIMILAR_TRACKS.storageValue -> PlaylistGenerationMode.SIMILAR_TRACKS.label()
    PlaylistGenerationMode.SIMILAR_ARTISTS.storageValue -> PlaylistGenerationMode.SIMILAR_ARTISTS.label()
    PlaylistGenerationMode.TAG.storageValue -> PlaylistGenerationMode.TAG.label()
    PlaylistGenerationMode.MIX.storageValue -> PlaylistGenerationMode.MIX.label()
    PlaylistGenerationMode.RECOMMENDATIONS.storageValue -> PlaylistGenerationMode.RECOMMENDATIONS.label()
    PlaylistGenerationMode.NEVER_HEARD.storageValue -> PlaylistGenerationMode.NEVER_HEARD.label()
    PlaylistGenerationMode.LIBRARY.storageValue -> PlaylistGenerationMode.LIBRARY.label()
    "similar-playlist" -> "Similar Playlist"
    else -> null
}

private fun PlaylistGenerationMode.label(): String = when (this) {
    PlaylistGenerationMode.TOP -> "Top"
    PlaylistGenerationMode.RECENT -> "Recent"
    PlaylistGenerationMode.SIMILAR_TRACKS -> "Similar Tracks"
    PlaylistGenerationMode.SIMILAR_ARTISTS -> "Similar Artists"
    PlaylistGenerationMode.TAG -> "Tag"
    PlaylistGenerationMode.MIX -> "Mix"
    PlaylistGenerationMode.RECOMMENDATIONS -> "Recommendations"
    PlaylistGenerationMode.NEVER_HEARD -> "Never Heard"
    PlaylistGenerationMode.LIBRARY -> "Library"
}
private fun PlaylistGenerationMode.description(): String = when (this) {
    PlaylistGenerationMode.TOP -> "Your most-played tracks from Mylo Replay."
    PlaylistGenerationMode.RECENT -> "Your latest listening history."
    PlaylistGenerationMode.SIMILAR_TRACKS -> "Choose a track and build a radio around its sound."
    PlaylistGenerationMode.SIMILAR_ARTISTS -> "Build a playlist around an artist you want to hear."
    PlaylistGenerationMode.TAG -> "Describe a mood or genre and discover matching music."
    PlaylistGenerationMode.MIX -> "Blend your taste with recommendations and an optional seed."
    PlaylistGenerationMode.RECOMMENDATIONS -> "Use your current YouTube Music recommendations."
    PlaylistGenerationMode.NEVER_HEARD -> "Find recommendations filtered against your listening history."
    PlaylistGenerationMode.LIBRARY -> "Tracks saved or liked in your YouTube Music library."
}


private fun ReplayPeriod.label(): String = when (this) {
    ReplayPeriod.ALL_TIME -> "All time"
    ReplayPeriod.THIS_YEAR -> "This year"
    ReplayPeriod.THIS_MONTH -> "This month"
    ReplayPeriod.THIS_WEEK -> "This week"
    else -> name.lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
}

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun ScrobblePlaylistsContent(
    viewModel: ScrobbleGenerateViewModel,
    hazeState: HazeState,
    contentPadding: PaddingValues,
    highlightedPlaylistId: String?,
    sharedHandoffFromGenerate: Boolean,
    reduceMotion: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    currentSong: Song?,
    isPlaying: Boolean,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOpenGenerator: () -> Unit,
) {
    val playlists by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    val storageError by LocalPlaylistStore.loadError.collectAsStateWithLifecycle()
    val generationState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(LocalPlaylistSortMode.NEWEST) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var expandedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var renameTarget by remember { mutableStateOf<LocalPlaylist?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalPlaylist?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var playlistActionError by remember { mutableStateOf<String?>(null) }

    val selected = playlists.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
    if (selected != null) {
        PlaylistDetailContent(
            playlist = selected,
            hazeState = hazeState,
            currentSong = currentSong,
            isPlaying = isPlaying,
            contentPadding = contentPadding,
            onBack = { selectedId = null },
            onPlaySongs = onPlaySongs,
            viewModel = viewModel,
            generationState = generationState,
            onRename = {
                renameText = selected.title
                renameError = null
                renameTarget = selected
            },
            onDelete = {
                deleteError = null
                deleteTarget = selected
            },
        )
    } else {
        val visible = sortLocalPlaylists(playlists, sortMode).filter { playlist ->
            search.isBlank() || playlist.title.contains(search, ignoreCase = true)
        }
        val totalTracks = playlists.sumOf { it.songs.size }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.scrobble_tab_playlists), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                "${playlists.size} playlists · ${totalTracks} tracks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = onOpenGenerator, colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSurface)) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.scrobble_tab_generate))
                        }
                        Box {
                            IconButton(onClick = { sortMenuExpanded = true }) {
                                Icon(Icons.Rounded.Sort, contentDescription = "Sort playlists")
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false },
                                containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 1f),
                            ) {
                                LocalPlaylistSortMode.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        leadingIcon = if (sortMode == option) {
                                            { Icon(Icons.Rounded.Check, contentDescription = null) }
                                        } else null,
                                        onClick = {
                                            sortMode = option
                                            sortMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Text(stringResource(R.string.scrobble_mixes_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            storageError?.let { error ->
                item(key = "playlist_storage_error") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { scope.launch { LocalPlaylistStore.reload() } }, colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSurface)) { Text(stringResource(R.string.retry)) }
                    }
                }
            }
            playlistActionError?.let { error ->
                item(key = "playlist_action_error") { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            generationState.savedPlaylist?.takeIf { it.generationMode == "similar-playlist" }?.let { saved ->
                item(key = "similar_playlist_saved") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Created ${saved.title}", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
                        TextButton(onClick = { selectedId = saved.id }, colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSurface)) { Text(stringResource(R.string.open)) }
                    }
                }
            }
            if (generationState.isGenerating) {
                item {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = scrobbleAccentColor())
                            Text(generationState.progress, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = viewModel::cancelGeneration, colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSecondaryContainer)) { Text(stringResource(R.string.cancel)) }
                        }
                    }
                }
            }
            generationState.error?.let { error ->
                item {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(error, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(14.dp))
                    }
                }
            }
            if (playlists.isNotEmpty()) {
                item {
                    SearchField(
                        query = search,
                        onQueryChange = { search = it },
                        onSubmit = {},
                        placeholder = "Find a playlist",
                    )
                }
            }
            if (visible.isEmpty() && storageError == null) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Icon(Icons.Rounded.LibraryMusic, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp))
                        Text(if (playlists.isEmpty()) "No playlists yet" else "No matching playlists", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (playlists.isEmpty()) "Create your first mix from the Generator." else "Try another playlist name.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        if (playlists.isEmpty()) {
                            Button(onClick = onOpenGenerator, colors = scrobblePrimaryButtonColors(), shape = RoundedCornerShape(16.dp)) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.scrobble_open_generator))
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(visible, key = { _, playlist -> playlist.id }) { index, playlist ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PlaylistRow(
                            playlist = playlist,
                            index = index,
                            lastIndex = visible.lastIndex,
                            showReorder = sortMode == LocalPlaylistSortMode.CUSTOM && search.isBlank(),
                            canGenerateSimilar = !generationState.isGenerating && playlist.songs.any { it.videoId.isNotBlank() && !it.isVideo },
                            onGenerateSimilar = { viewModel.generateSimilar(playlist) },
                            animateHandoff = playlist.id == highlightedPlaylistId,
                            sharedHandoffFromGenerate = sharedHandoffFromGenerate,
                            reduceMotion = reduceMotion,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onMoveUp = {
                                scope.launch {
                                    playlistActionError = if (LocalPlaylistStore.movePlaylist(playlist.id, index - 1)) null
                                    else "Couldn't save playlist order. Try again."
                                }
                            },
                            onMoveDown = {
                                scope.launch {
                                    playlistActionError = if (LocalPlaylistStore.movePlaylist(playlist.id, index + 1)) null
                                    else "Couldn't save playlist order. Try again."
                                }
                            },
                            onOpen = { selectedId = playlist.id },
                            onPlay = { onPlaySongs(playlist.songs, 0) },
                            onPin = {
                                scope.launch {
                                    playlistActionError = if (LocalPlaylistStore.setPinned(playlist.id, !playlist.isPinned)) null
                                    else "Couldn't update playlist pin. Try again."
                                }
                            },
                            onRename = {
                                renameText = playlist.title
                                renameError = null
                                renameTarget = playlist
                            },
                            onDelete = {
                                deleteError = null
                                deleteTarget = playlist
                            },
                        )
                        if (playlist.songs.isNotEmpty()) {
                            TextButton(
                                onClick = { expandedPlaylistId = if (expandedPlaylistId == playlist.id) null else playlist.id },
                                colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.align(Alignment.End),
                            ) {
                                Text(if (expandedPlaylistId == playlist.id) "Hide preview" else "Preview tracks")
                            }
                            AnimatedVisibility(visible = expandedPlaylistId == playlist.id) {
                                Column(
                                    Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    playlist.songs.take(3).forEachIndexed { trackIndex, song ->
                                        Row(
                                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                                .clickable { onPlaySongs(playlist.songs, trackIndex) }
                                                .padding(vertical = 4.dp, horizontal = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            AsyncImage(
                                                model = song.thumbnailUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(7.dp)),
                                            )
                                            Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                                Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text((trackIndex + 1).toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    TextButton(onClick = { selectedId = playlist.id }, colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSurface)) {
                                        Text("View all ${playlist.songs.size} tracks")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }

    renameTarget?.let { target ->
        AlertScaffold(
            hazeState = hazeState,
            onDismiss = { renameTarget = null },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Rename playlist",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Choose a name for this playlist.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                PillTextField(
                    value = renameText,
                    onValueChange = { renameText = it; renameError = null },
                    placeholder = "Playlist name",
                )
                renameError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            AlertRule()
            AlertAction(
                label = stringResource(R.string.save),
                contentColor = scrobbleAccentColor(),
                emphasised = true,
                onClick = {
                    val clean = renameText.trim()
                    val duplicate = playlists.any { it.id != target.id && it.title.equals(clean, ignoreCase = true) }
                    when {
                        clean.isBlank() -> renameError = "Enter a playlist name"
                        duplicate -> renameError = "A playlist with that name already exists"
                        else -> scope.launch {
                            if (LocalPlaylistStore.renamePlaylist(target.id, clean)) {
                                renameTarget = null
                            } else {
                                renameError = "Couldn't save playlist name. Try again."
                            }
                        }
                    }
                },
            )
            AlertRule()
            AlertAction(
                label = stringResource(R.string.cancel),
                contentColor = MaterialTheme.colorScheme.onSurface,
                emphasised = false,
                onClick = { renameTarget = null },
            )
        }
    }
    deleteTarget?.let { target ->
        AlertScaffold(
            hazeState = hazeState,
            onDismiss = { deleteTarget = null; deleteError = null },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Delete playlist?",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.W600),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "${target.title} will be removed from this device. Listening history is not affected.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 17.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
            AlertRule()
            AlertAction(
                label = stringResource(R.string.delete),
                contentColor = MaterialTheme.colorScheme.error,
                emphasised = true,
                destructive = true,
                onClick = {
                    scope.launch {
                        withContext<Unit>(NonCancellable) {
                            if (LocalPlaylistStore.deletePlaylist(target.id)) {
                                target.customCoverUri?.let { savedUri ->
                                    runCatching {
                                        context.contentResolver.releasePersistableUriPermission(
                                            Uri.parse(savedUri),
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                        )
                                    }
                                }
                                if (selectedId == target.id) selectedId = null
                                deleteTarget = null
                                deleteError = null
                            } else {
                                deleteError = "Couldn't delete playlist. Check device storage and try again."
                            }
                        }
                    }
                },
            )
            AlertRule()
            AlertAction(
                label = stringResource(R.string.cancel),
                contentColor = MaterialTheme.colorScheme.onSurface,
                emphasised = false,
                onClick = { deleteTarget = null; deleteError = null },
            )
        }
    }
}


@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun PlaylistRow(
    playlist: LocalPlaylist,
    index: Int,
    lastIndex: Int,
    showReorder: Boolean,
    canGenerateSimilar: Boolean,
    animateHandoff: Boolean,
    sharedHandoffFromGenerate: Boolean,
    reduceMotion: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onGenerateSimilar: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember(playlist.id) { mutableStateOf(false) }
    val sharedBoundsModifier = if (animateHandoff && sharedHandoffFromGenerate && !reduceMotion) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                rememberSharedContentState(key = "scrobble-playlist-" + playlist.id),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else {
        Modifier
    }
    val rowShape = RoundedCornerShape(18.dp)
    val gradientEnabled = LocalScrobbleGradientEnabled.current
    val rowContent: @Composable () -> Unit = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .then(sharedBoundsModifier)
                    .then(
                        if (gradientEnabled) {
                            Modifier.border(1.dp, Color.White.copy(alpha = 0.08f), rowShape)
                        } else {
                            Modifier
                        },
                    ),
                shape = rowShape,
                color = if (gradientEnabled) Color.White.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = playlist.customCoverUri ?: playlist.songs.firstOrNull()?.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen),
                )
                Column(
                    Modifier.weight(1f).clickable(onClick = onOpen).padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    // Keep the title on its own line: the mode badge must never
                    // consume the width needed to identify a long playlist.
                    Text(
                        playlist.title,
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Text(
                            "${playlist.songs.size} tracks${if (playlist.isPinned) " · Pinned" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        playlistGenerationModeLabel(playlist.generationMode)?.let { modeLabel ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ) {
                                Text(
                                    modeLabel,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onPlay, enabled = playlist.songs.isNotEmpty()) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play playlist")
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist actions")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 1f),
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (playlist.isPinned) "Unpin" else "Pin") },
                            leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                            onClick = { menuExpanded = false; onPin() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scrobble_generate_similar)) },
                            leadingIcon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                            enabled = canGenerateSimilar,
                            onClick = { menuExpanded = false; onGenerateSimilar() },
                        )
                        if (showReorder) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.scrobble_move_up)) },
                                leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                                enabled = index > 0,
                                onClick = { menuExpanded = false; onMoveUp() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.scrobble_move_down)) },
                                leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                                enabled = index < lastIndex,
                                onClick = { menuExpanded = false; onMoveDown() },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { menuExpanded = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
            }
    }
    if (animateHandoff && !reduceMotion && !sharedHandoffFromGenerate) {
        val handoffVisibility = remember(playlist.id) {
            MutableTransitionState(false).apply { targetState = true }
        }
        AnimatedVisibility(
            visibleState = handoffVisibility,
            enter = fadeIn(tween(220)) +
                slideInVertically(tween(380)) { fullHeight -> fullHeight / 2 } +
                scaleIn(tween(380), initialScale = 0.92f),
        ) {
            rowContent()
        }
    } else {
        rowContent()
    }
}

@Composable
private fun PlaylistDetailContent(
    playlist: LocalPlaylist,
    hazeState: HazeState,
    currentSong: Song?,
    isPlaying: Boolean,
    contentPadding: PaddingValues,
    viewModel: ScrobbleGenerateViewModel,
    generationState: ScrobbleGenerateUiState,
    onBack: () -> Unit,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var addTracksExpanded by remember(playlist.id) { mutableStateOf(false) }
    var addTrackQuery by remember(playlist.id) { mutableStateOf("") }
    var addTrackResults by remember(playlist.id) { mutableStateOf<List<Song>>(emptyList()) }
    var addTrackLoading by remember(playlist.id) { mutableStateOf(false) }
    var addTrackError by remember(playlist.id) { mutableStateOf<String?>(null) }
    var addTrackMessage by remember(playlist.id) { mutableStateOf<String?>(null) }
    var detailMenuExpanded by remember(playlist.id) { mutableStateOf(false) }
    var coverMessage by remember(playlist.id) { mutableStateOf<String?>(null) }
    var trackActionMessage by remember(playlist.id) { mutableStateOf<String?>(null) }
    var exportDialogVisible by remember(playlist.id) { mutableStateOf(false) }
    var exportMessage by remember(playlist.id) { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val existingVideoIds = remember(playlist.songs) { playlist.songs.mapTo(hashSetOf()) { it.videoId } }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val newUri = uri.toString()
            val oldUri = playlist.customCoverUri
            scope.launch {
                var saved = false
                try {
                    if (newUri != oldUri) {
                        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    withContext<Unit>(NonCancellable) {
                        saved = LocalPlaylistStore.setCustomCover(playlist.id, newUri)
                        if (saved) {
                            oldUri?.takeIf { it != newUri }?.let { staleUri ->
                                runCatching {
                                    context.contentResolver.releasePersistableUriPermission(
                                        Uri.parse(staleUri),
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                    )
                                }
                            }
                            coverMessage = "Cover updated"
                        } else {
                            if (newUri != oldUri) {
                                runCatching {
                                    context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                            }
                            coverMessage = if (LocalPlaylistStore.getPlaylist(playlist.id) == null) {
                                "Playlist no longer exists"
                            } else {
                                "Couldn't save cover. Try again."
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    if (!saved && newUri != oldUri) {
                        runCatching {
                            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    throw cancelled
                } catch (error: Exception) {
                    if (!saved && newUri != oldUri) {
                        runCatching {
                            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                    }
                    coverMessage = error.message ?: "Couldn't update cover"
                }
            }
        }
    }

    LaunchedEffect(playlist.id, addTracksExpanded, addTrackQuery) {
        val query = addTrackQuery.trim()
        if (!addTracksExpanded || query.length < 2) {
            addTrackResults = emptyList()
            addTrackError = null
            addTrackLoading = false
            return@LaunchedEffect
        }
        delay(300)
        addTrackLoading = true
        addTrackError = null
        try {
            val response = YtMusicRepository.search(query, SearchFilter.SONGS).getOrThrow()
            currentCoroutineContext().ensureActive()
            addTrackResults = response.mapNotNull { result ->
                when (result) {
                    is SearchResult.TopTrack -> result.song
                    is SearchResult.Track -> result.song
                    is SearchResult.Browse -> null
                }
            }.filter { it.videoId.isNotBlank() && !it.isVideo }
                .distinctBy { it.videoId }
                .take(8)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            addTrackResults = emptyList()
            addTrackError = error.message ?: "Couldn't search YouTube Music"
        } finally {
            addTrackLoading = false
        }
    }


    val listState = rememberLazyListState()
    val currentTrackId = currentSong?.videoId?.takeIf { it.isNotBlank() }
    val currentTrackDisplayIndex = if (isPlaying && currentTrackId != null) {
        playlist.songs.indexOfFirst { it.videoId == currentTrackId }
    } else -1
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Back to playlists")
                }
                if (currentTrackDisplayIndex >= 0) {
                    TextButton(
                        onClick = {
                            val precedingItems = 1 +
                                (if (generationState.isGenerating) 1 else 0) +
                                (if (generationState.error != null) 1 else 0) +
                                (if (addTracksExpanded) 1 + addTrackResults.size else 0)
                            scope.launch {
                                listState.animateScrollToItem(precedingItems + currentTrackDisplayIndex)
                            }
                        },
                        colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSurface),
                    ) {
                        Icon(Icons.Rounded.MyLocation, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.scrobble_locate))
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        addTracksExpanded = !addTracksExpanded
                        if (!addTracksExpanded) {
                            addTrackQuery = ""
                            addTrackResults = emptyList()
                            addTrackMessage = null
                        }
                    },
                    colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSurface),
                ) { Text(if (addTracksExpanded) "Done" else "Add tracks") }
                Box {
                    IconButton(onClick = { detailMenuExpanded = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Playlist actions")
                    }
                    DropdownMenu(
                        expanded = detailMenuExpanded,
                        onDismissRequest = { detailMenuExpanded = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 1f),
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scrobble_change_cover)) },
                            leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null) },
                            onClick = {
                                detailMenuExpanded = false
                                coverPicker.launch(arrayOf("image/*"))
                            },
                        )
                        if (playlist.customCoverUri != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.scrobble_reset_cover)) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = {
                                    detailMenuExpanded = false
                                    val oldUri = playlist.customCoverUri
                                    scope.launch {
                                        withContext<Unit>(NonCancellable) {
                                            if (LocalPlaylistStore.setCustomCover(playlist.id, null)) {
                                                oldUri?.let { savedUri ->
                                                    runCatching {
                                                        context.contentResolver.releasePersistableUriPermission(
                                                            Uri.parse(savedUri),
                                                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                                        )
                                                    }
                                                }
                                                coverMessage = "Cover reset"
                                            } else {
                                                coverMessage = "Couldn't reset cover. Try again."
                                            }
                                        }
                                    }
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scrobble_generate_similar)) },
                            leadingIcon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                            enabled = !generationState.isGenerating && playlist.songs.any { it.videoId.isNotBlank() && !it.isVideo },
                            onClick = {
                                detailMenuExpanded = false
                                viewModel.generateSimilar(playlist)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.scrobble_export_playlist)) },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            onClick = {
                                detailMenuExpanded = false
                                exportDialogVisible = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.rename)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { detailMenuExpanded = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { detailMenuExpanded = false; onDelete() },
                        )
                    }
                }
            }

            LocalPlaylistReleaseHeader(
                playlistId = playlist.id,
                title = playlist.title,
                thumbnailUrl = playlist.customCoverUri ?: playlist.songs.firstOrNull()?.thumbnailUrl,
                songs = playlist.songs,
                onPlay = { onPlaySongs(playlist.songs, 0) },
                onShuffle = { onPlaySongs(playlist.songs.shuffled(), 0) },
            )

            coverMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (it == "Cover updated" || it == "Cover reset") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
            }
            trackActionMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            generationState.savedPlaylist?.takeIf { it.generationMode == "similar-playlist" }?.let {
                Text("Created ${it.title}. Go back to view it.", style = MaterialTheme.typography.bodySmall, color = scrobbleAccentColor())
            }
            exportMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (it.startsWith("Couldn't")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }




        }

        if (generationState.isGenerating) {
            item(key = "similar_generation_progress") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = scrobbleAccentColor())
                    Text(generationState.progress, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = viewModel::cancelGeneration, colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSecondaryContainer)) { Text(stringResource(R.string.cancel)) }
                }
            }
        }
        generationState.error?.let { error ->
            item(key = "similar_generation_error") { Text(error, color = MaterialTheme.colorScheme.error) }
        }
        if (addTracksExpanded) {
            item(key = "add_tracks_search") {
                SearchField(
                    query = addTrackQuery,
                    onQueryChange = {
                        addTrackQuery = it
                        addTrackError = null
                        addTrackMessage = null
                    },
                    onSubmit = {},
                    placeholder = "Search YouTube Music",
                )
                if (addTrackLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = scrobbleAccentColor())
                addTrackError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                addTrackMessage?.let {
                    Text(
                        it,
                        color = if (it.startsWith("Couldn't") || it.contains("no longer")) MaterialTheme.colorScheme.error
                        else scrobbleAccentColor(),
                    )
                }
                if (!addTrackLoading && addTrackError == null && addTrackQuery.trim().length >= 2 && addTrackResults.isEmpty()) {
                    Text(stringResource(R.string.scrobble_no_tracks_found), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(addTrackResults, key = { "add-${it.videoId}" }) { song ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Column(
                        Modifier.weight(1f).padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            song.artist,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val alreadyAdded = song.videoId in existingVideoIds
                    TextButton(
                        enabled = !alreadyAdded,
                        colors = scrobbleTextButtonColors(MaterialTheme.colorScheme.onSurface),
                        onClick = {
                            scope.launch {
                                val added = LocalPlaylistStore.addSongIfAbsent(playlist.id, song)
                                val current = LocalPlaylistStore.getPlaylist(playlist.id)
                                addTrackMessage = when {
                                    added -> "Added ${song.title}"
                                    current == null -> "Playlist no longer exists"
                                    current.songs.any { it.videoId == song.videoId } -> "Track already in this playlist"
                                    else -> "Couldn't save track. Try again."
                                }
                            }
                        },
                    ) { Text(if (alreadyAdded) "Added" else "Add") }
                }
            }
        }
        if (playlist.songs.isEmpty()) {
            item {
                Text(
                    "This playlist is empty.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 28.dp),
                )
            }
        } else {
            itemsIndexed(
                playlist.songs,
                key = { _, song -> "${playlist.id}-${song.videoId}" },
            ) { index, song ->
                val isCurrentTrack = isPlaying && currentTrackId != null && song.videoId == currentTrackId
                var trackMenuExpanded by remember(playlist.id, song.videoId) { mutableStateOf(false) }
                SongRow(
                    song = song,
                    onClick = { onPlaySongs(playlist.songs, index) },
                    onLongPress = { trackMenuExpanded = true },
                    onMore = { trackMenuExpanded = true },
                    isCurrent = isCurrentTrack,
                    isPlaying = isCurrentTrack && isPlaying,
                    activeTint = scrobbleAccentColor(),
                )
                DropdownMenu(
                    expanded = trackMenuExpanded,
                    onDismissRequest = { trackMenuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 1f),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.scrobble_move_up)) },
                        leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                        enabled = index > 0,
                        onClick = {
                            trackMenuExpanded = false
                            scope.launch {
                                trackActionMessage = if (LocalPlaylistStore.moveSong(playlist.id, index, song.videoId, -1)) null
                                else "Couldn't save track order. Try again."
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.scrobble_move_down)) },
                        leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                        enabled = index < playlist.songs.lastIndex,
                        onClick = {
                            trackMenuExpanded = false
                            scope.launch {
                                trackActionMessage = if (LocalPlaylistStore.moveSong(playlist.id, index, song.videoId, 1)) null
                                else "Couldn't save track order. Try again."
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.scrobble_remove_from_playlist)) },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = {
                            trackMenuExpanded = false
                            scope.launch {
                                trackActionMessage = if (LocalPlaylistStore.removeSong(playlist.id, index, song.videoId)) null
                                else "Couldn't remove track. Try again."
                            }
                        },
                    )
                }
            }
        }
    }
    if (exportDialogVisible) {
        ScrobblePlaylistExportDialog(
            playlist = playlist,
            hazeState = hazeState,
            onDismiss = { exportDialogVisible = false },
            onMessage = { exportMessage = it },
        )
    }

}

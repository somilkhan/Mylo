package com.music.bitchord.ui.scrobble

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.music.bitchord.R
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistSortMode
import com.music.bitchord.data.spotify.LocalPlaylistStore
import com.music.bitchord.data.spotify.sortLocalPlaylists
import com.music.bitchord.ui.components.AlertAction
import com.music.bitchord.ui.components.AlertRule
import com.music.bitchord.ui.components.AlertScaffold
import com.music.bitchord.ui.components.IosMenuPopover
import com.music.bitchord.ui.components.IosMenuRow
import com.music.bitchord.ui.components.IosMenuSeparator
import com.music.bitchord.ui.components.PAGE_GUTTER
import com.music.bitchord.ui.components.backdrop.Backdrop
import com.music.bitchord.ui.components.PillTextField
import com.music.bitchord.ui.components.SearchField
import com.music.bitchord.ui.icons.SfIcons
import com.music.bitchord.ui.scrobble.stats.Artwork
import com.music.bitchord.ui.scrobble.stats.StatsMessage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@get:StringRes
internal val LocalPlaylistSortMode.labelRes: Int
    get() = when (this) {
        LocalPlaylistSortMode.NEWEST -> R.string.scrobble_sort_newest
        LocalPlaylistSortMode.OLDEST -> R.string.scrobble_sort_oldest
        LocalPlaylistSortMode.NAME -> R.string.scrobble_sort_name
        LocalPlaylistSortMode.TRACK_COUNT -> R.string.scrobble_sort_track_count
        LocalPlaylistSortMode.CUSTOM -> R.string.scrobble_sort_custom
    }

private fun LocalPlaylist.cover(): String? = customCoverUri?.takeIf { it.isNotBlank() }
    ?: songs.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl

/** A persisted URI grant belongs to the app, not to an individual playlist. */
private fun releaseUnusedCover(context: android.content.Context, savedUri: String?) {
    if (savedUri == null || LocalPlaylistStore.playlists.value.any { it.customCoverUri == savedUri }) return
    runCatching {
        context.contentResolver.releasePersistableUriPermission(
            Uri.parse(savedUri), Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

private fun LocalPlaylist.canSeedSimilar(): Boolean = songs.any { it.videoId.isNotBlank() && !it.isVideo }

/** A row menu, and where it hangs from. */
private class RowMenu(val playlist: LocalPlaylist, val index: Int)

/**
 * The Playlists tab: every playlist Generate saved, plus any made by hand,
 * as a plain list in the Library's style. Opening one uses Mylo's native
 * release-detail navigation; sort, pin, rename and delete stay in this tab.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ScrobblePlaylistsContent(
    viewModel: ScrobbleGenerateViewModel,
    hazeState: HazeState,
    pageBackdrop: Backdrop?,
    contentPadding: PaddingValues,
    highlightedPlaylistId: String?,
    sharedHandoffFromGenerate: Boolean,
    reduceMotion: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onPlaySongs: (List<Song>, Int) -> Unit,
    onOpenGenerator: () -> Unit,
    onOpenPlaylist: (LocalPlaylist) -> Unit,
) {
    val playlists by LocalPlaylistStore.playlists.collectAsStateWithLifecycle()
    val storageError by LocalPlaylistStore.loadError.collectAsStateWithLifecycle()
    val generationState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(LocalPlaylistSortMode.NEWEST) }
    var renameTarget by remember { mutableStateOf<LocalPlaylist?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<LocalPlaylist?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val playlistListState = rememberLazyListState()

    val startRename: (LocalPlaylist) -> Unit = {
        renameText = it.title
        renameError = null
        renameTarget = it
    }
    val startDelete: (LocalPlaylist) -> Unit = {
        deleteError = null
        deleteTarget = it
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        PlaylistList(
            playlists = playlists,
            storageError = storageError,
            actionError = actionError,
            generationState = generationState,
            query = query,
            onQueryChange = { query = it },
            sortMode = sortMode,
            onSortMode = { sortMode = it },
            contentPadding = contentPadding,
            highlightedPlaylistId = highlightedPlaylistId,
            sharedHandoffFromGenerate = sharedHandoffFromGenerate,
            reduceMotion = reduceMotion,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            onRetryStorage = { scope.launch { LocalPlaylistStore.reload() } },
            onOpen = onOpenPlaylist,
            onPlay = { onPlaySongs(it.songs, 0) },
            onGenerateSimilar = viewModel::generateSimilar,
            onCancelGeneration = viewModel::cancelGeneration,
            onOpenGenerator = onOpenGenerator,
            onPin = { playlist ->
                scope.launch {
                    actionError = if (LocalPlaylistStore.setPinned(playlist.id, !playlist.isPinned)) null
                    else context.getString(R.string.scrobble_pin_failed)
                }
            },
            onMove = { playlist, to ->
                val from = playlists.indexOfFirst { it.id == playlist.id }
                val direction = (to - from).coerceIn(-1, 1)
                scope.launch {
                    actionError = if (from >= 0 && LocalPlaylistStore.movePlaylistBy(playlist.id, direction)) null
                    else context.getString(R.string.scrobble_order_save_failed)
                }
            },
            onRename = startRename,
            onDelete = startDelete,
            listState = playlistListState,
            hazeState = hazeState,
            pageBackdrop = pageBackdrop,
        )
    }

    renameTarget?.let { target ->
        AlertScaffold(hazeState = hazeState, onDismiss = { if (!renaming) renameTarget = null }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.scrobble_rename_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    stringResource(R.string.scrobble_rename_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.heightIn(min = 4.dp))
                PillTextField(
                    value = renameText,
                    onValueChange = { renameText = it; renameError = null },
                    placeholder = stringResource(R.string.scrobble_rename_placeholder),
                )
                renameError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            AlertRule()
            AlertAction(
                label = stringResource(R.string.save),
                contentColor = MaterialTheme.colorScheme.primary,
                emphasised = true,
                enabled = !renaming,
                onClick = {
                    if (renaming) return@AlertAction
                    val clean = renameText.trim()
                    val duplicate = playlists.any { it.id != target.id && it.title.equals(clean, ignoreCase = true) }
                    when {
                        clean.isBlank() -> renameError = context.getString(R.string.scrobble_rename_empty)
                        duplicate -> renameError = context.getString(R.string.scrobble_rename_duplicate)
                        else -> {
                            renaming = true
                            scope.launch {
                                try {
                                    if (LocalPlaylistStore.renamePlaylist(target.id, clean)) {
                                        renameTarget = null
                                    } else {
                                        renameError = context.getString(R.string.scrobble_rename_failed)
                                    }
                                } finally {
                                    renaming = false
                                }
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
                enabled = !renaming,
                onClick = { renameTarget = null },
            )
        }
    }
    deleteTarget?.let { target ->
        AlertScaffold(hazeState = hazeState, onDismiss = { if (!deleting) { deleteTarget = null; deleteError = null } }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 19.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.scrobble_delete_named_title, target.title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.scrobble_remove_device_message, target.title) + " " +
                        stringResource(R.string.scrobble_delete_undo_note),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                deleteError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            AlertRule()
            AlertAction(
                label = stringResource(R.string.delete),
                contentColor = MaterialTheme.colorScheme.error,
                emphasised = true,
                destructive = true,
                enabled = !deleting,
                onClick = {
                    if (deleting) return@AlertAction
                    deleting = true
                    scope.launch {
                        try {
                            withContext<Unit>(NonCancellable) {
                                val cover = LocalPlaylistStore.getPlaylist(target.id)?.customCoverUri
                                if (LocalPlaylistStore.deletePlaylist(target.id)) {
                                    releaseUnusedCover(context, cover)
                                    deleteTarget = null
                                    deleteError = null
                                } else {
                                    deleteError = context.getString(R.string.scrobble_delete_failed)
                                }
                            }
                        } finally {
                            deleting = false
                        }
                    }
                },
            )
            AlertRule()
            AlertAction(
                label = stringResource(R.string.cancel),
                contentColor = MaterialTheme.colorScheme.onSurface,
                emphasised = false,
                enabled = !deleting,
                onClick = { deleteTarget = null; deleteError = null },
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun PlaylistList(
    playlists: List<LocalPlaylist>,
    storageError: String?,
    actionError: String?,
    generationState: ScrobbleGenerateUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    sortMode: LocalPlaylistSortMode,
    onSortMode: (LocalPlaylistSortMode) -> Unit,
    contentPadding: PaddingValues,
    highlightedPlaylistId: String?,
    sharedHandoffFromGenerate: Boolean,
    reduceMotion: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onRetryStorage: () -> Unit,
    onOpen: (LocalPlaylist) -> Unit,
    onPlay: (LocalPlaylist) -> Unit,
    onGenerateSimilar: (LocalPlaylist) -> Unit,
    onCancelGeneration: () -> Unit,
    onOpenGenerator: () -> Unit,
    onPin: (LocalPlaylist) -> Unit,
    onMove: (LocalPlaylist, Int) -> Unit,
    onRename: (LocalPlaylist) -> Unit,
    onDelete: (LocalPlaylist) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    hazeState: HazeState? = null,
    pageBackdrop: Backdrop? = null,
) {
    val colors = MaterialTheme.colorScheme
    val anchorView = LocalView.current
    var sortOpen by remember { mutableStateOf(false) }
    var sortAnchor by remember { mutableStateOf<Rect?>(null) }
    var rowMenu by remember { mutableStateOf<RowMenu?>(null) }
    var rowAnchor by remember { mutableStateOf<Rect?>(null) }
    val visible = sortLocalPlaylists(playlists, sortMode).filter { playlist ->
        query.isBlank() || playlist.title.contains(query.trim(), ignoreCase = true)
    }
    val canReorder = sortMode == LocalPlaylistSortMode.CUSTOM && query.isBlank()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
            .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier),
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 32.dp),
    ) {
        item(key = "header") {
            ScrobblePageTitle(
                title = stringResource(R.string.scrobble_tab_playlists),
                subtitle = if (playlists.isEmpty()) {
                    stringResource(R.string.scrobble_mixes_subtitle)
                } else {
                    stringResource(R.string.scrobble_playlists_tracks_summary, playlists.size, playlists.sumOf { it.songs.size })
                },
                topPadding = contentPadding.calculateTopPadding(),
                trailing = {
                    if (playlists.isNotEmpty()) {
                        ScrobbleIconButton(
                            icon = SfIcons.ChevronUpDown,
                            description = stringResource(R.string.scrobble_sort_playlists),
                            onClick = { sortOpen = true },
                            active = sortOpen,
                            onAnchor = { sortAnchor = it.toScreenCoordinates(anchorView) },
                        )
                    }
                    ScrobbleIconButton(
                        icon = SfIcons.AutoAwesome,
                        description = stringResource(R.string.scrobble_open_generator),
                        onClick = onOpenGenerator,
                    )
                },
            )
        }

        val notices = buildList {
            storageError?.let { add("storage") }
            actionError?.let { add("action") }
            if (generationState.isGenerating) add("generating")
            if (!generationState.isGenerating && generationState.error != null &&
                generationState.activeRequest == null
            ) add("error")
            generationState.savedPlaylist?.takeIf { it.generationMode == "similar-playlist" && !generationState.isGenerating }
                ?.let { add("similar") }
        }
        if (notices.isNotEmpty()) {
            item(key = "notices") {
                Column(
                    Modifier.padding(horizontal = PAGE_GUTTER).padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    notices.forEach { notice ->
                        when (notice) {
                            "storage" -> ScrobbleStatusCard(
                                title = stringResource(R.string.scrobble_playlists_storage_error),
                                body = null,
                                icon = SfIcons.ErrorOutline,
                                iconTint = colors.error,
                                action = stringResource(R.string.retry),
                                onAction = onRetryStorage,
                            )
                            "action" -> ScrobbleStatusCard(
                                title = actionError.orEmpty(),
                                body = null,
                                icon = SfIcons.ErrorOutline,
                                iconTint = colors.error,
                            )
                            "generating" -> ScrobbleStatusCard(
                                title = stringResource(R.string.scrobble_building_mix),
                                body = generationState.progress.ifBlank { stringResource(R.string.scrobble_gen_loading_fallback) },
                                busy = true,
                                action = stringResource(R.string.cancel),
                                onAction = onCancelGeneration,
                            )
                            "error" -> ScrobbleStatusCard(
                                title = stringResource(R.string.scrobble_gen_failed_title),
                                body = generationState.error,
                                icon = SfIcons.ErrorOutline,
                                iconTint = colors.error,
                            )
                            "similar" -> generationState.savedPlaylist?.let { saved ->
                                ScrobbleStatusCard(
                                    title = saved.title,
                                    body = stringResource(R.string.scrobble_gen_saved_body, saved.songs.size),
                                    action = stringResource(R.string.open),
                                    onAction = { onOpen(saved) },
                                    leading = {
                                        Artwork(saved.cover(), SfIcons.PlaylistPlay, 44.dp, RoundedCornerShape(8.dp))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        if (storageError != null) return@LazyColumn

        if (playlists.isNotEmpty()) {
            item(key = "search") {
                SearchField(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSubmit = {},
                    placeholder = stringResource(R.string.scrobble_search_placeholder),
                    modifier = Modifier.padding(horizontal = PAGE_GUTTER).padding(top = 16.dp, bottom = 8.dp),
                )
            }
        }

        if (visible.isEmpty()) {
            item(key = "empty") {
                if (playlists.isEmpty()) {
                    StatsMessage(
                        icon = SfIcons.PlaylistPlay,
                        title = stringResource(R.string.scrobble_playlists_empty_title),
                        body = stringResource(R.string.scrobble_playlists_empty_body),
                        action = stringResource(R.string.scrobble_open_generator),
                        onAction = onOpenGenerator,
                        modifier = Modifier.padding(top = 56.dp),
                    )
                } else {
                    StatsMessage(
                        icon = SfIcons.LibraryMusic,
                        title = stringResource(R.string.scrobble_playlists_no_match_title),
                        body = stringResource(R.string.scrobble_playlists_no_match_body),
                        modifier = Modifier.padding(top = 40.dp),
                    )
                }
            }
        } else {
            itemsIndexed(visible, key = { _, playlist -> playlist.id }) { index, playlist ->
                val handoff = playlist.id == highlightedPlaylistId
                val sharedModifier = if (handoff && sharedHandoffFromGenerate && !reduceMotion) {
                    with(sharedTransitionScope) {
                        Modifier.sharedBounds(
                            rememberSharedContentState(key = "scrobble-playlist-" + playlist.id),
                            animatedVisibilityScope = animatedVisibilityScope,
                        )
                    }
                } else {
                    Modifier
                }
                val row: @Composable () -> Unit = {
                    PlaylistRow(
                        playlist = playlist,
                        menuOpen = rowMenu?.playlist?.id == playlist.id,
                        onOpen = { onOpen(playlist) },
                        onMenu = { anchor ->
                            rowAnchor = anchor
                            rowMenu = RowMenu(playlist, index)
                        },
                        modifier = sharedModifier,
                    )
                }
                if (handoff && !reduceMotion && !sharedHandoffFromGenerate) {
                    val state = remember(playlist.id) { MutableTransitionState(false).apply { targetState = true } }
                    AnimatedVisibility(
                        visibleState = state,
                        enter = fadeIn(tween(220)) +
                            slideInVertically(tween(380)) { it / 2 } +
                            scaleIn(tween(380), initialScale = 0.94f),
                    ) { row() }
                } else {
                    row()
                }
            }
        }
    }

    if (sortOpen) Dialog(
        onDismissRequest = { sortOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Keep the menu above app navigation while sampling the same live page backdrop.
        IosMenuPopover(item = sortMode, anchor = sortAnchor, onDismiss = { sortOpen = false }, backdrop = pageBackdrop, screenCoordinates = true, bottomClearance = ScrobbleChromeClearance) { current ->
        LocalPlaylistSortMode.entries.forEach { mode ->
            IosMenuRow(
                label = stringResource(mode.labelRes),
                icon = null,
                onClick = {
                    sortOpen = false
                    onSortMode(mode)
                },
                trailing = if (mode == current) {
                    {
                        Icon(
                            SfIcons.Check,
                            contentDescription = null,
                            tint = colors.onSurface,
                            modifier = Modifier.padding(start = 10.dp).size(16.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
        }
    }

    val currentRowMenu = remember(rowMenu, visible) {
        rowMenu?.let { menu ->
            visible.firstOrNull { it.id == menu.playlist.id }?.let { current ->
                RowMenu(current, visible.indexOfFirst { it.id == current.id })
            }
        }
    }
    IosMenuPopover(item = currentRowMenu, anchor = rowAnchor, onDismiss = { rowMenu = null }, hazeState = hazeState, bottomClearance = ScrobbleChromeClearance) { menu ->
        val playlist = menu.playlist
        val close = { rowMenu = null }
        IosMenuRow(
            label = stringResource(R.string.play),
            icon = SfIcons.Play,
            enabled = playlist.songs.isNotEmpty(),
            onClick = { close(); onPlay(playlist) },
        )
        IosMenuRow(
            label = stringResource(R.string.scrobble_generate_similar),
            icon = SfIcons.AutoAwesome,
            enabled = !generationState.isGenerating && playlist.canSeedSimilar(),
            onClick = { close(); onGenerateSimilar(playlist) },
        )
        IosMenuSeparator()
        IosMenuRow(
            label = stringResource(if (playlist.isPinned) R.string.scrobble_unpin else R.string.scrobble_pin),
            icon = if (playlist.isPinned) SfIcons.Unpin else SfIcons.Pin,
            onClick = { close(); onPin(playlist) },
        )
        IosMenuRow(
            label = stringResource(R.string.rename),
            icon = SfIcons.Pencil,
            onClick = { close(); onRename(playlist) },
        )
        if (canReorder) {
            IosMenuRow(
                label = stringResource(R.string.scrobble_move_up),
                icon = SfIcons.ArrowUp,
                enabled = menu.index > 0,
                onClick = { close(); onMove(playlist, menu.index - 1) },
            )
            IosMenuRow(
                label = stringResource(R.string.scrobble_move_down),
                icon = SfIcons.ArrowDown,
                enabled = menu.index < visible.lastIndex,
                onClick = { close(); onMove(playlist, menu.index + 1) },
            )
        }
        IosMenuSeparator()
        IosMenuRow(
            label = stringResource(R.string.delete),
            icon = SfIcons.Trash,
            iconTint = colors.error,
            labelColor = colors.error,
            onClick = { close(); onDelete(playlist) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    playlist: LocalPlaylist,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onMenu: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var moreAnchor by remember { mutableStateOf<Rect?>(null) }
    var rowAnchor by remember { mutableStateOf<Rect?>(null) }
    val mode = playlistGenerationModeLabelRes(playlist.generationMode)?.let { stringResource(it) }
    val meta = listOfNotNull(stringResource(R.string.scrobble_tracks_count, playlist.songs.size), mode).joinToString(" · ")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowAnchor = it.boundsInWindow() }
            .combinedClickable(
                role = Role.Button,
                onClick = onOpen,
                onLongClick = { onMenu(rowAnchor) },
            )
            .padding(start = PAGE_GUTTER, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(playlist.cover(), SfIcons.PlaylistPlay, 56.dp, RoundedCornerShape(8.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                playlist.title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playlist.isPinned) {
                    Icon(
                        SfIcons.Pin,
                        contentDescription = stringResource(R.string.scrobble_pinned),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.padding(end = 5.dp).size(11.dp),
                    )
                }
                Text(
                    meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ScrobbleIconButton(
            icon = SfIcons.More,
            description = stringResource(R.string.scrobble_more_options, playlist.title),
            onClick = { onMenu(moreAnchor) },
            active = menuOpen,
            filled = false,
            onAnchor = { moreAnchor = it },
        )
    }
}

/** The bar, the header, and the status block come before the first track. */
private const val TRACKS_OFFSET = 3

internal fun playlistTrackListIndex(trackIndex: Int, hasAddResults: Boolean): Int =
    TRACKS_OFFSET + trackIndex + if (hasAddResults) 1 else 0

/** Imported playlists may legitimately contain the same track more than once. */
internal fun playlistTrackRowKeys(playlist: LocalPlaylist): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return playlist.songs.map { song ->
        val occurrence = occurrences.getOrDefault(song.videoId, 0)
        occurrences[song.videoId] = occurrence + 1
        "${playlist.id}-${song.videoId}-$occurrence"
    }
}

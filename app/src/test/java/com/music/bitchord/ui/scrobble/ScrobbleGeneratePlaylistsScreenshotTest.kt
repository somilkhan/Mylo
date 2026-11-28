package com.music.bitchord.ui.scrobble

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.music.bitchord.R
import com.music.bitchord.data.generate.PlaylistGenerationMode
import com.music.bitchord.data.model.Song
import com.music.bitchord.data.scrobbling.LastFmStatsPeriod
import com.music.bitchord.data.spotify.LocalPlaylist
import com.music.bitchord.data.spotify.LocalPlaylistSortMode
import com.music.bitchord.ui.AndroidAppUiHost
import com.music.bitchord.ui.AppUi
import com.music.bitchord.ui.player.AndroidPlayerHost
import com.music.bitchord.ui.player.PlayerPlatform
import com.music.bitchord.ui.theme.BitChordTheme
import dev.chrisbanes.haze.HazeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Draws the real Generate, its sheet and playlist list with sample data into
 * `app/build/screenshots/` for design review.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-night-xxhdpi")
class ScrobbleGeneratePlaylistsScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun installHosts() {
        PlayerPlatform.install(AndroidPlayerHost(RuntimeEnvironment.getApplication()))
        AppUi.install(AndroidAppUiHost)
    }

    private val connected = GenerateAccounts(true, "somil", youTubeMusicSignedIn = true)
    private val padding = PaddingValues(top = 56.dp, bottom = 120.dp)

    private fun song(i: Int, title: String, artist: String) =
        Song(videoId = "v$i", title = title, artist = artist, thumbnailUrl = "https://art/$i")

    private val songs = listOf(
        song(1, "Midnight City", "M83"),
        song(2, "Nights", "Frank Ocean"),
        song(3, "Redbone", "Childish Gambino"),
        song(4, "Breathe Deeper", "Tame Impala"),
    )

    @Test fun generate() = capture("generate_page") { page(ScrobbleGenerateUiState(), connected) }

    @Test fun generateNotConnected() = capture("generate_not_connected") {
        page(ScrobbleGenerateUiState(), GenerateAccounts(false, "", youTubeMusicSignedIn = false))
    }

    @Test fun generateRunning() = capture("generate_running") {
        page(ScrobbleGenerateUiState(isGenerating = true, progress = "Reading somil’s top tracks…"), connected)
    }

    @Test fun generateSaved() = capture("generate_saved") {
        page(
            ScrobbleGenerateUiState(
                savedPlaylist = LocalPlaylist("p1", "My Top Tracks · Last 30 days", songs, generationMode = "top"),
            ),
            connected,
        )
    }

    @Test fun sheetTop() = capture("generate_sheet_top") {
        sheet(GenerateForm(mode = PlaylistGenerationMode.TOP, period = LastFmStatsPeriod.MONTH))
    }

    @Test fun sheetTag() = capture("generate_sheet_tag") {
        sheet(GenerateForm(mode = PlaylistGenerationMode.TAG_TOP, tag = "indie"))
    }

    @Test fun sheetSeed() = capture("generate_sheet_seed") {
        sheet(
            GenerateForm(mode = PlaylistGenerationMode.SIMILAR_TRACKS, seedQuery = "nights", seed = songs[1]),
            search = SeedSearch(results = songs.drop(1)),
            currentSong = songs[0],
        )
    }

    @Test fun sheetNeverHeard() = capture("generate_sheet_never_heard") {
        sheet(GenerateForm(mode = PlaylistGenerationMode.NEVER_HEARD, excludeLiked = true), state = ScrobbleGenerateUiState(isGenerating = true, progress = "Checking 1,240 scrobbled tracks…"))
    }

    @Test fun playlists() = capture("playlists_list") {
        list(
            listOf(
                LocalPlaylist("a", "My Top Tracks · All time", songs, 3L, "top", isPinned = true),
                LocalPlaylist("b", "Recent scrobbles", songs.reversed(), 2L, "recent"),
                LocalPlaylist("c", "Similar to Nights", songs.shuffled(java.util.Random(4)), 1L, "similar-tracks"),
                LocalPlaylist("d", "indie Top Tracks", songs.drop(1), 0L, "tag-top"),
                LocalPlaylist("e", "Road trip", songs.take(2)),
            ),
        )
    }

    @Test fun playlistsEmpty() = capture("playlists_empty") { list(emptyList()) }

    private val detailPlaylist = LocalPlaylist("detail", "Late night rotation", songs, generationMode = "top")

    @Test fun playlistOpenDelegatesToNativeDetail() = capture("playlist_native_open", verify = {
        compose.onNodeWithText(detailPlaylist.title).performClick()
        assertEquals(detailPlaylist, openedPlaylist)
    }) {
        list(listOf(detailPlaylist), onOpen = { openedPlaylist = it })
    }

    private var openedPlaylist: LocalPlaylist? = null

    @Composable
    private fun page(state: ScrobbleGenerateUiState, accounts: GenerateAccounts) {
        GenerateScreen(
            uiState = state,
            accounts = accounts,
            contentPadding = padding,
            onOpenMode = {}, onCancel = {}, onRetry = {}, onDismissError = {},
            onOpenPlaylists = {}, onConnectLastFm = {},
        )
    }

    @Composable
    private fun sheet(
        form: GenerateForm,
        search: SeedSearch = SeedSearch(),
        currentSong: Song? = null,
        state: ScrobbleGenerateUiState = ScrobbleGenerateUiState(),
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 24.dp, bottom = 24.dp),
            ) {
                GenerateSheetContent(
                    form = form, onFormChange = {}, search = search, currentSong = currentSong,
                    uiState = state, onGenerate = {}, onCancel = {},
                )
            }
        }
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Composable
    private fun list(playlists: List<LocalPlaylist>, onOpen: (LocalPlaylist) -> Unit = {}) {
        SharedTransitionLayout {
            AnimatedVisibility(visible = true) {
                PlaylistList(
                    playlists = playlists, storageError = null, actionError = null,
                    generationState = ScrobbleGenerateUiState(), query = "", onQueryChange = {},
                    sortMode = LocalPlaylistSortMode.NEWEST, onSortMode = {}, contentPadding = padding,
                    highlightedPlaylistId = null, sharedHandoffFromGenerate = false, reduceMotion = true,
                    sharedTransitionScope = this@SharedTransitionLayout, animatedVisibilityScope = this,
                    onRetryStorage = {}, onOpen = onOpen, onPlay = {}, onGenerateSimilar = {},
                    onCancelGeneration = {}, onOpenGenerator = {}, onPin = {}, onMove = { _, _ -> },
                    onRename = {}, onDelete = {},
                )
            }
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    private fun capture(
        name: String,
        darkTheme: Boolean = true,
        verify: () -> Unit = {},
        content: @Composable () -> Unit,
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { request -> cover(request.data.toString()) },
            ) {
                BitChordTheme(darkTheme = darkTheme) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitForIdle()
        verify()
        compose.waitForIdle()
        val root = compose.activity.window.decorView
        assertTrue("The real activity must be measured before capture", root.width > 0 && root.height > 0)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File(System.getProperty("mylo.screenshots") ?: "build/screenshots").apply { mkdirs() }
        val output = File(dir, "$name.png")
        output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        assertTrue("The real composable screenshot must be written", output.length() > 0)
        bitmap.recycle()
    }

    private fun cover(seed: String): coil3.Image {
        val palette = listOf(
            0xFF3A4A5C to 0xFF141A22, 0xFF6B3E2E to 0xFF1E120D, 0xFF2F5045 to 0xFF0F1A16,
            0xFF5A4B6E to 0xFF17121F, 0xFF7A6A48 to 0xFF221D12, 0xFF41566B to 0xFF0E141B,
        )
        val (a, b) = palette[Math.floorMod(seed.hashCode(), palette.size)]
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(
            0f, 0f, 300f, 300f,
            Paint().apply { shader = LinearGradient(0f, 0f, 300f, 300f, a.toInt(), b.toInt(), Shader.TileMode.CLAMP) },
        )
        return bitmap.asImage()
    }
}

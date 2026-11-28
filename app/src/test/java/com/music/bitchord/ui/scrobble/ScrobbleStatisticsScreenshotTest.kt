package com.music.bitchord.ui.scrobble

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.scrobbling.LastFmStatsPeriod
import com.music.bitchord.data.scrobbling.LastFmStatsSnapshot
import com.music.bitchord.ui.theme.BitChordTheme
import org.robolectric.RuntimeEnvironment
import com.music.bitchord.ui.AndroidAppUiHost
import com.music.bitchord.ui.AppUi
import com.music.bitchord.ui.player.AndroidPlayerHost
import com.music.bitchord.ui.player.PlayerPlatform
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the Statistics screen with sample data and writes PNGs to
 * `app/build/screenshots/` for design review. Nothing is asserted beyond
 * the screen composing and drawing without throwing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@ConscryptMode(ConscryptMode.Mode.OFF)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp-night-xxhdpi")
class ScrobbleStatisticsScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val now = 1_795_000_000_000L

    @Before fun installHosts() {
        // BitChordApplication does this at startup; the shared components read settings through them.
        PlayerPlatform.install(AndroidPlayerHost(RuntimeEnvironment.getApplication()))
        AppUi.install(AndroidAppUiHost)
    }
    private val nowSeconds = now / 1000

    @Test fun loaded() = capture("stats_loaded", sampleState())

    @Test fun recentFolded() = capture("stats_recent_folded", sampleState(), collapse = 1f)

    @Test fun topTracks() = capture("stats_top_tracks", sampleState().copy(listMode = StatsListMode.TOP_TRACKS), collapse = 1f)

    @Test fun topArtists() = capture("stats_top_artists", sampleState().copy(listMode = StatsListMode.TOP_ARTISTS), collapse = 1f)

    @Test fun topAlbums() = capture("stats_top_albums", sampleState().copy(listMode = StatsListMode.TOP_ALBUMS))

    @Test fun listMenu() = capture("stats_list_menu", sampleState(), collapse = 1f, menuOpen = true)

    @Test fun halfFolded() = capture("stats_half_folded", sampleState(), collapse = 0.5f)

    @Test fun loadedWeek() = capture("stats_loaded_week", sampleState().let { state ->
        state.copy(
            period = LastFmStatsPeriod.WEEK,
            snapshotPeriod = LastFmStatsPeriod.WEEK,
            snapshot = state.snapshot!!.copy(
                scrobbles = 312L,
                previousScrobbles = 355L,
                activity = listOf(38L, 52L, 41L, 0L, 67L, 74L, 40L).mapIndexed { i, plays ->
                    val start = nowSeconds - 7 * 86_400L + i * 86_400L
                    com.music.bitchord.data.scrobbling.LastFmActivityBucket(start, start + 86_399L, plays)
                },
            ),
        )
    })

    @Test fun notConnected() = capture("stats_not_connected", ScrobbleStatisticsUiState(connected = false))

    @Test fun loading() = capture("stats_loading", ScrobbleStatisticsUiState(connected = true, username = "somil", isLoading = true))

    @OptIn(ExperimentalCoilApi::class)
    private fun capture(
        name: String,
        state: ScrobbleStatisticsUiState,
        collapse: Float = 0f,
        menuOpen: Boolean = false,
    ) {
        // Shimmer and the now-playing bars animate forever; drive the clock by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { request ->
                    cover(request.data.toString())
                },
            ) {
                BitChordTheme(darkTheme = true) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        ScrobbleStatisticsScreen(
                            state = state,
                            actions = StatsActions({}, {}, {}, {}, {}, { _, _ -> }, { _, _ -> }),
                            contentPadding = PaddingValues(top = 56.dp, bottom = 120.dp),
                            nowMillis = now,
                            initialCollapse = collapse,
                            initialMenuOpen = menuOpen,
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        // captureToImage() waits on a hardware redraw Robolectric never signals; draw the view directly.
        val root = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        val dir = File(System.getProperty("mylo.screenshots") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Stand-in cover art: a two-tone diagonal wash seeded from the URL. */
    private fun cover(seed: String): coil3.Image {
        val palette = listOf(
            0xFF3A4A5C to 0xFF141A22, 0xFF6B3E2E to 0xFF1E120D, 0xFF2F5045 to 0xFF0F1A16,
            0xFF5A4B6E to 0xFF17121F, 0xFF7A6A48 to 0xFF221D12, 0xFF41566B to 0xFF0E141B,
            0xFF6E3A4C to 0xFF1D0F14, 0xFF4E5D3A to 0xFF141910,
        )
        val (a, b) = palette[Math.floorMod(seed.hashCode(), palette.size)]
        val bitmap = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawRect(
            0f, 0f, 300f, 300f,
            Paint().apply { shader = LinearGradient(0f, 0f, 300f, 300f, a.toInt(), b.toInt(), Shader.TileMode.CLAMP) },
        )
        return bitmap.asImage()
    }

    private fun sampleState(): ScrobbleStatisticsUiState {
        val artists = listOf(
            "Radiohead" to 412L, "Fleetwood Mac" to 301L, "Arctic Monkeys" to 254L, "Frank Ocean" to 198L,
            "Tame Impala" to 167L, "Daft Punk" to 120L, "Bon Iver" to 96L,
        ).mapIndexed { i, (n, p) -> LastFM.RankedArtist(n, p, "sample://artist/$i") }
        val tracks = listOf(
            Triple("Weird Fishes/Arpeggi", "Radiohead", 64L), Triple("Dreams", "Fleetwood Mac", 51L),
            Triple("Do I Wanna Know?", "Arctic Monkeys", 47L), Triple("Pink + White", "Frank Ocean", 39L),
            Triple("The Less I Know the Better", "Tame Impala", 33L), Triple("Holocene", "Bon Iver", 21L),
        ).mapIndexed { i, (t, a, p) -> LastFM.RankedTrack(t, a, p, "sample://track/$i") }
        val albums = listOf(
            "In Rainbows" to "Radiohead", "Rumours" to "Fleetwood Mac", "AM" to "Arctic Monkeys",
            "Blonde" to "Frank Ocean", "Currents" to "Tame Impala",
        ).mapIndexed { i, (t, a) -> LastFM.RankedAlbum(t, a, 120L - i * 17, "sample://album/$i") }
        val hour = 3_600L
        val day = 86_400L
        val recent = listOf(
            LastFM.RecentTrack("Nude", "Radiohead", "sample://album/0", 0L, nowPlaying = true, album = "In Rainbows"),
            LastFM.RecentTrack("Go Your Own Way", "Fleetwood Mac", "sample://album/1", nowSeconds - 240),
            LastFM.RecentTrack("Ivy", "Frank Ocean", "sample://album/3", nowSeconds - 1_500),
            LastFM.RecentTrack("Let It Happen", "Tame Impala", "sample://album/4", nowSeconds - 2 * hour),
            LastFM.RecentTrack("Arabella", "Arctic Monkeys", "sample://album/2", nowSeconds - day - hour),
            LastFM.RecentTrack("Reckoner", "Radiohead", "sample://album/0", nowSeconds - day - 3 * hour),
            LastFM.RecentTrack("Self Control", "Frank Ocean", "sample://album/3", nowSeconds - day - 4 * hour),
            LastFM.RecentTrack("Holocene", "Bon Iver", "sample://album/5", nowSeconds - 3 * day),
            LastFM.RecentTrack("Dreams", "Fleetwood Mac", "sample://album/1", nowSeconds - 3 * day - hour),
            LastFM.RecentTrack("Digital Love", "Daft Punk", "sample://album/6", nowSeconds - 9 * day),
            LastFM.RecentTrack("Weird Fishes/Arpeggi", "Radiohead", "sample://album/0", nowSeconds - 9 * day - hour),
        )
        val snapshot = LastFmStatsSnapshot(
            profile = LastFM.UserStats("somil", "Somil", 48_213L, 6_120L, 1_402L, 2_310L, null, 1_420_000_000L),
            scrobbles = 1_284L,
            artistCount = 212L,
            albumCount = 388L,
            trackCount = 731L,
            topArtists = artists,
            topTracks = tracks,
            topAlbums = albums,
            activity = listOf(96L, 141L, 118L, 162L, 104L, 87L, 133L, 214L, 158L, 71L).mapIndexed { i, plays ->
                val start = nowSeconds - 30 * 86_400L + i * 3 * 86_400L
                com.music.bitchord.data.scrobbling.LastFmActivityBucket(start, start + 3 * 86_400L - 1, plays)
            },
            previousScrobbles = 1_146L,
        )
        return ScrobbleStatisticsUiState(
            period = LastFmStatsPeriod.MONTH,
            connected = true,
            username = "somil",
            snapshot = snapshot,
            snapshotPeriod = LastFmStatsPeriod.MONTH,
            recent = RecentFeed(items = recent, page = 1, totalPages = 1),
        )
    }
}

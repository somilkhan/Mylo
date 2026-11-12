package com.music.bitchord.data.sync

import android.content.Context
import com.music.bitchord.auth.AuthStore
import com.music.bitchord.auth.GoogleAccountSession
import com.music.bitchord.data.LibrarySyncCategory
import com.music.bitchord.data.YtMusicRepository
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.settings.AppSettings
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Shared account-library transaction used by foreground UI and WorkManager. */
class AccountLibrarySyncRunner(context: Context) {
    private val appContext = context.applicationContext
    private val store = AccountLibrarySnapshotStore(File(appContext.filesDir, "account-library-snapshots"))

    data class Outcome(val result: SyncResult, val status: String, val snapshot: AccountLibrarySnapshot?)

    suspend fun run(
        session: GoogleAccountSession,
        profileId: String,
        force: Boolean,
        powerSave: Boolean,
        metered: Boolean?,
        hasValidatedInternet: Boolean,
        onProgress: (String, Set<LibrarySyncCategory>) -> Unit = { _, _ -> },
    ): Outcome = InnertubeSessionSyncLock.mutex.withLock sessionLock@ {
        val profile = session.profiles.firstOrNull { it.profileId == profileId }
            ?: return@sessionLock Outcome(SyncResult.FAILED, "Selected profile is unavailable", null)
        val identity = "${session.accountId}:${profile.profileId}"
        AccountLibrarySyncLock.lockFor(identity).withLock accountLock@ {
            try {
                // Revalidate after acquiring the session mutex: a queued worker must
                // never resurrect credentials captured before a profile/account switch.
                val active = AuthStore(appContext).activeSession
                val activeProfile = active?.activeProfileId ?: active?.profiles?.firstOrNull()?.profileId
                if (active == null || active.accountId != session.accountId ||
                    active.cookie != session.cookie ||
                    activeProfile != profileId
                ) {
                    return@accountLock Outcome(SyncResult.NOTHING, "Active account changed; sync skipped", null)
                }

                Innertube.cookie = session.cookie
                Innertube.selectChannel(profile.pageId, profile.dataSyncId, profile.authUser)
                Innertube.ensureSessionScope()
                YtMusicRepository.setAccountSyncMode(AppSettings.accountSyncMode.value)

                val cached = store.read(identity)
                val now = System.currentTimeMillis()
                if (!force && !AppSettings.autoAccountSync.value) {
                    return@accountLock Outcome(SyncResult.NOTHING, "Automatic sync is disabled", cached)
                }
                val selected = AppSettings.accountSyncCategories.value
                val categories = if (force) selected else {
                    if (!automaticSyncEligible(
                            hasInternet = hasValidatedInternet,
                            validated = hasValidatedInternet,
                            powerSave = powerSave,
                            metered = metered,
                            mode = AppSettings.accountSyncBatteryMode.value,
                        )
                    ) {
                        return@accountLock Outcome(
                            SyncResult.NOTHING,
                            "Automatic sync waiting for an eligible connection",
                            cached,
                        )
                    }
                    AppSettings.accountSyncCategoriesDue(
                        identity, selected, now,
                        automaticSyncCooldownMs(AppSettings.accountSyncBatteryMode.value),
                    )
                }
                if (categories.isEmpty()) {
                    return@accountLock Outcome(SyncResult.NOTHING, "Selected categories are up to date", cached)
                }

                val activeCategories = ConcurrentHashMap.newKeySet<LibrarySyncCategory>()
                onProgress("Fetching selected account categories…", emptySet())
                val remote = YtMusicRepository.libraryForSync(categories) { category, active ->
                    if (active) activeCategories.add(category) else activeCategories.remove(category)
                    val labels = activeCategories.joinToString { it.name.lowercase().replace('_', ' ') }
                    onProgress(
                        if (labels.isBlank()) "Fetching selected account categories…" else "Syncing $labels…",
                        activeCategories.toSet(),
                    )
                }.getOrElse {
                    return@accountLock Outcome(SyncResult.FAILED, "Library request failed; previous data kept", cached)
                }
                val likedSongs = (if (LibrarySyncCategory.LIKED_SONGS in categories) {
                    remote.likedSongs
                } else cached?.library?.likedSongs.orEmpty()).toMutableList()
                var likedToken = if (LibrarySyncCategory.LIKED_SONGS in categories) remote.likedContinuation else null
                val seenLikedTokens = HashSet<String>()
                var complete = true
                if (likedToken != null && likedToken.isBlank()) {
                    complete = false
                    likedToken = null
                }
                if (likedToken != null) {
                    activeCategories.add(LibrarySyncCategory.LIKED_SONGS)
                    onProgress("Importing remaining liked songs…", activeCategories.toSet())
                }
                try {
                    while (likedToken != null) {
                    if (likedToken.isBlank() || !seenLikedTokens.add(likedToken)) {
                        complete = false
                        break
                    }
                    val pageResult = YtMusicRepository.moreSongs(likedToken)
                    if (pageResult.isFailure) {
                        complete = false
                        break
                    }
                    val page = pageResult.getOrThrow()
                    likedSongs += page.songs
                    likedToken = page.continuation
                    }
                } finally {
                    activeCategories.remove(LibrarySyncCategory.LIKED_SONGS)
                    onProgress("Fetching selected account categories…", activeCategories.toSet())
                }

                val historySections = mutableListOf<InnertubeParser.HistorySection>()
                var historyToken: String? = null
                if (LibrarySyncCategory.LISTENING_HISTORY in categories) {
                    activeCategories.add(LibrarySyncCategory.LISTENING_HISTORY)
                    onProgress("Importing complete listening history…", activeCategories.toSet())
                    val first = YtMusicRepository.historyPage().getOrElse {
                        complete = false
                        null
                    }
                    historyToken = first?.continuation
                    if (historyToken?.isBlank() == true) {
                        complete = false
                        historyToken = null
                    }
                    first?.let { appendHistorySections(historySections, it.sections) }
                    val seenHistoryTokens = HashSet<String>()
                    while (historyToken != null) {
                        if (historyToken.isBlank() || !seenHistoryTokens.add(historyToken)) {
                            complete = false
                            break
                        }
                        val page = YtMusicRepository.moreHistory(historyToken).getOrElse {
                            complete = false
                            break
                        }
                        appendHistorySections(historySections, page.sections)
                        historyToken = page.continuation
                    }
                    activeCategories.remove(LibrarySyncCategory.LISTENING_HISTORY)
                    onProgress("Fetching selected account categories…", activeCategories.toSet())
                } else {
                    historySections.addAll(cached?.historySections.orEmpty())
                }

                val hadHistory = cached?.historySections?.any { it.songs.isNotEmpty() } == true
                if (hadHistory && historySections.none { it.songs.isNotEmpty() }) complete = false
                val recentSongs = historySections.flatMap { it.songs }.take(20)
                val overwrite = AppSettings.accountSyncOverwriteWithRemote.value
                val syncedLiked = mergeRemoteRows(
                    cached?.library?.likedSongs.orEmpty(), likedSongs.distinctBy { it.videoId }, overwrite,
                ) { it.videoId }
                val likedIds = syncedLiked.mapTo(HashSet()) { it.videoId }
                val replacedTitles = buildSet {
                    if (LibrarySyncCategory.ALBUMS in categories) add("Albums")
                    if (LibrarySyncCategory.ARTISTS in categories) add("Artists")
                    if (LibrarySyncCategory.PLAYLISTS in categories) add(YtMusicRepository.PLAYLISTS_SHELF)
                    if (LibrarySyncCategory.RECENT_ACTIVITY in categories) add("Recent activity")
                    add("Podcasts")
                }
                val syncedLibrarySongs = mergeRemoteRows(
                    cached?.library?.librarySongs.orEmpty(),
                    if (LibrarySyncCategory.PRIVATE_SONGS in categories) remote.librarySongs
                    else cached?.library?.librarySongs.orEmpty(),
                    overwrite,
                ) { it.videoId }.filterNot { it.videoId in likedIds }
                val remoteTitles = remote.shelves.mapTo(HashSet()) { it.title }
                val preserved = cached?.library?.shelves.orEmpty().filter { shelf ->
                    shelf.title !in replacedTitles || shelf.title !in remoteTitles ||
                        remote.shelves.firstOrNull { it.title == shelf.title }?.items.isNullOrEmpty()
                }
                val mergedShelves = remote.shelves.map { shelf ->
                    val localItems = cached?.library?.shelves?.firstOrNull { it.title == shelf.title }?.items.orEmpty()
                    shelf.copy(items = mergeRemoteRows(localItems, shelf.items, overwrite) {
                        it.browseId ?: it.videoId ?: "${it.title}\\n${it.subtitle}"
                    })
                }
                val library = remote.copy(
                    likedSongs = syncedLiked,
                    librarySongs = syncedLibrarySongs,
                    shelves = preserved + mergedShelves,
                    likedContinuation = null,
                )
                if (!complete) {
                    return@accountLock Outcome(SyncResult.FAILED, "Sync incomplete; previous saved data kept", cached)
                }
                val remoteCount = syncedLiked.size + library.librarySongs.size + library.shelves.sumOf { it.items.size }
                if (cached != null && !mayRemoveMissing(complete = true, remoteCount = remoteCount)) {
                    return@accountLock Outcome(SyncResult.FAILED, "Empty refresh rejected; previous data kept", cached)
                }

                onProgress("Saving offline account snapshot…", emptySet())
                val snapshot = AccountLibrarySnapshot(library, historySections, recentSongs, now)
                store.write(identity, snapshot)
                AppSettings.markAccountSyncCategoriesSynced(identity, categories, now)
                Outcome(
                    SyncResult.SYNCED,
                    if (LibrarySyncCategory.LISTENING_HISTORY in categories) {
                        "Synced ${historySections.sumOf { it.songs.size }} history plays"
                    } else "Library sync completed",
                    snapshot,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Outcome(SyncResult.FAILED, "Sync incomplete; previous saved data kept", store.read(identity))
            }
        }
    }

    private fun appendHistorySections(
        target: MutableList<InnertubeParser.HistorySection>,
        incoming: List<InnertubeParser.HistorySection>,
    ) {
        if (incoming.isEmpty()) return
        val first = incoming.first()
        val tail = target.lastOrNull()
        if (tail != null && (first.title.isBlank() || first.title == tail.title)) {
            target[target.lastIndex] = tail.copy(songs = tail.songs + first.songs)
            target += incoming.drop(1)
        } else target += incoming
    }
}

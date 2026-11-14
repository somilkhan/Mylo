package com.music.bitchord.data.sync

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InnertubeSessionSyncLockTest {

    @Test
    fun profileSwitchWaitsForEveryHistoryPageInTheCurrentSync() = runTest {
        val events = mutableListOf<String>()

        val sync = launch {
            InnertubeSessionSyncLock.mutex.withLock {
                events += "history-page-1"
                delay(100)
                events += "history-page-2"
                delay(100)
                events += "snapshot-saved"
            }
        }
        val profileSwitch = launch {
            InnertubeSessionSyncLock.mutex.withLock {
                events += "profile-switched"
            }
        }

        advanceUntilIdle()
        sync.join()
        profileSwitch.join()

        assertEquals(
            listOf("history-page-1", "history-page-2", "snapshot-saved", "profile-switched"),
            events,
        )
    }
}

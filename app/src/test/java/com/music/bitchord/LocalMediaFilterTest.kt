package com.music.bitchord

import com.music.bitchord.data.LocalMediaRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaFilterTest {

    @Test
    fun disablingNonMusicFilterDoesNotApplyMediaStoreMusicFlagGate() {
        assertEquals(null, LocalMediaRepository.localMusicSelection(filterNonMusic = false))
        assertTrue(LocalMediaRepository.localMusicSelection(filterNonMusic = true)!!.contains("IS_MUSIC"))
    }

    @Test
    fun enabledFilterKeepsUnknownDurationAndExcludesSystemAudioKinds() {
        val selection = requireNotNull(LocalMediaRepository.localMusicSelection(filterNonMusic = true))
        assertTrue(selection.contains("IS_MUSIC != 0"))
        assertTrue(selection.contains("DURATION >= ?"))
        assertTrue(selection.contains("DURATION <= 0"))
        assertTrue(selection.contains("DURATION IS NULL"))
        assertTrue(selection.contains("IS_ALARM = 0"))
        assertTrue(selection.contains("IS_NOTIFICATION = 0"))
        assertTrue(selection.contains("IS_RINGTONE = 0"))
        assertTrue(selection.contains("IS_PODCAST = 0"))
    }

    @Test
    fun rejectsShortAudio() {
        assertFalse(
            LocalMediaRepository.isEligibleLocalMusic(
                durationMs = 29_999,
                displayName = "effect.mp3",
                path = "/storage/emulated/0/Music/effect.mp3",
            ),
        )
    }

    @Test
    fun rejectsWaveAndRecorderFiles() {
        assertFalse(
            LocalMediaRepository.isEligibleLocalMusic(
                durationMs = 180_000,
                displayName = "recording.wav",
                path = "/storage/emulated/0/Recordings/recording.wav",
            ),
        )
        assertFalse(
            LocalMediaRepository.isEligibleLocalMusic(
                durationMs = 180_000,
                displayName = "voice-note.mp3",
                path = "/storage/emulated/0/Voice Recorder/voice-note.mp3",
            ),
        )
    }

    @Test
    fun keepsSupportedMusicFiles() {
        assertTrue(
            LocalMediaRepository.isEligibleLocalMusic(
                durationMs = 50_000,
                displayName = "Song.flac",
                path = "/storage/emulated/0/Music/Artist/Song.flac",
            ),
        )
    }

    @Test
    fun keepsAudioOnlyMp4AndDsd() {
        for (name in listOf("Song.mp4", "Song.dsf", "Song.dff")) {
            assertTrue(
                name,
                LocalMediaRepository.isEligibleLocalMusic(
                    durationMs = 180_000,
                    displayName = name,
                    path = "/storage/emulated/0/Music/$name",
                ),
            )
        }
    }

    @Test
    fun filenameFallbackRemovesExtensionCaseInsensitively() {
        assertTrue(
            LocalMediaRepository.isEligibleLocalMusic(60_000, "Song.MP3", null),
        )
    }

    @Test
    fun filenameFallbackRemovesEverySupportedAudioExtension() {
        for (extension in listOf("mp3", "m4a", "mp4", "flac", "wav", "ogg", "opus", "aac", "webm", "dsf", "dff", "3gp")) {
            assertTrue(
                extension,
                LocalMediaRepository.isEligibleLocalMusic(
                    durationMs = 60_000,
                    displayName = "Song.$extension",
                    path = null,
                ),
            )
        }
    }

    @Test
    fun scannerRecognizesAllEligibleAudioContainers() {
        for (name in listOf("Song.mp3", "Song.m4a", "Song.mp4", "Song.flac", "Song.ogg", "Song.opus", "Song.aac", "Song.webm", "Song.dsf", "Song.dff", "Song.3gp")) {
            assertTrue(name, LocalMediaRepository.isAudioFileName(name))
        }
        assertFalse(LocalMediaRepository.isAudioFileName("cover.jpg"))
    }

    @Test
    fun keepsTracksTheScannerCouldNotTime() {
        // Fragmented-MP4 ALAC scans with duration 0, high-rate DSD with NULL
        // (read back as 0): an unknown length, not a short clip.
        assertTrue(
            LocalMediaRepository.isEligibleLocalMusic(
                durationMs = 0,
                displayName = "Starboy.m4a",
                path = "/storage/emulated/0/Music/Starboy.m4a",
            ),
        )
    }
}

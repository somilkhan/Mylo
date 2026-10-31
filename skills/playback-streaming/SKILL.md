# MYLO Playback / Streaming Engineering

## Source map

- Media3 player, session and foreground playback lifecycle: `app/src/main/java/com/music/bitchord/playback/PlaybackService.kt`.
- Player connection and UI bridge: `playback/PlayerConnection.kt` and `ui/player/AndroidPlayerHost.kt`.
- Queue coordination and queue logic: `playback/QueueCoordinator.kt`, `playback/BoundedQueuePlanner.kt`, `playback/BoundedQueueEdits.kt`.
- Online source integration includes `data/innertube/` and `data/jiosaavn/JioSaavnService.kt`; local audio paths include `playback/smart/LocalAudioSource.kt`.
- Listen Together clock/state transport: `data/listentogether/ListenTogether.kt`; player synchronization: `playback/PartySync.kt`.

## Diagnostic sequence

1. Reproduce and classify the source: local file, cached/downloaded audio, or online stream.
2. Trace selected track identity and resolved stream URL through repository → queue/player request → Media3 media item.
3. Separate source lookup failure, HTTP/data-source failure, decoder failure, player state transition and UI synchronization.
4. For queue issues, verify current item identity, index changes, repeat/shuffle semantics and queue persistence.
5. For Listen Together, inspect party sequence numbers, server-clock offset, socket reconnect and `PartySync`; do not synchronize by assuming network arrival equals playback start.
6. Add a regression test at the lowest layer that can reproduce the defect, then verify integration behavior separately.

## Useful tests

```bash
./gradlew --no-daemon :app:testDevDebugUnitTest --tests "com.music.bitchord.AudioPipelineTest"
./gradlew --no-daemon :app:testDevDebugUnitTest --tests "com.music.bitchord.PlaybackFallbackTest"
./gradlew --no-daemon :app:testDevDebugUnitTest --tests "com.music.bitchord.QueueCoordinatorTest"
./gradlew --no-daemon :app:testDevDebugUnitTest --tests "com.music.bitchord.playback.audio.DirectAudioStreamingRegressionTest"
```

Confirm exact test class names in `app/src/test` before invoking a focused test.

## Listening-history timestamps

- `ListeningRecorder.onStarted` is called from Media3 audible-state and track-transition callbacks. That event supplies the timestamp for a Mylo play.
- The five-second progress sampler contributes listened duration and decides when a play qualifies; never stamp a play at the threshold-crossing sample.
- Keep `onStopped` wired to pause/stop/track transitions so paused wall-clock gaps are not counted. Preserve local-file identity via `Song.localUri`.
- Regression coverage lives in `app/src/test/java/com/music/bitchord/data/stats/ListeningRecorderTest.kt`; run it with `:app:testDevDebugUnitTest --tests com.music.bitchord.data.stats.ListeningRecorderTest`.

## Evidence boundaries

Do not treat a selected codec, advertised output capability, DSP format, telemetry event or successful build as proof of sound reaching a physical endpoint. Bluetooth/USB/device routing requires the relevant real hardware to verify. Keep fixes narrow; the playback service and queue are high-risk shared state.

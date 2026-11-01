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

## Scrobble recording and track identity

- ListeningRecorder.onStarted is triggered by the player's audible/transition callback. It starts the event clock immediately and launches metadata enrichment early; the five-second progress sampler decides whether the event qualifies, but the persisted play timestamp is the audible start, not the threshold-crossing sample.
- Qualification threshold is max(30 seconds, min(track duration / 2, 4 minutes)) when runtime is known, otherwise 30 seconds. A qualifying event is recorded once per uninterrupted playback; seeking does not add listening time, and onStopped resets the clock across pause/stop/track changes.
- Enrichment is non-blocking and process-cached. Only query YtMusicRepository.trackLinks for an 11-character YouTube video ID; local URI/path tracks and other source-module IDs must not generate catalogue requests. The sample is never held while the lookup is in flight.
- Persist enriched title/artist/artwork/duration/artist ID/album ID/album name/video-origin fields with the Play event. Keep disk writes on the recorder's IO scope, never the playback sampler thread.
- Track identity precedence is localUri → localPath → YouTube videoId → normalized title/artist fallback. A local file can have a matched YouTube video ID; never merge two distinct local files on that ID.
- PlayHistoryStore keeps exact Mylo events at distinct timestamps but reconciles overlapping imported records by track/local day. Preserve this asymmetry: it prevents one-day import overlap from inflating imported counts without erasing actual repeat plays.
- Focused regression tests: ListeningRecorderTest (clock/timestamp semantics), ScrobbleGeneratorTest (identity, dedupe, metadata TSV compatibility), and ScrobbleMixTest (playable Play → Song conversion). Run the complete :app:testDevDebugUnitTest task after changes.

## Evidence boundaries

Do not treat a selected codec, advertised output capability, DSP format, telemetry event or successful build as proof of sound reaching a physical endpoint. Bluetooth/USB/device routing requires the relevant real hardware to verify. Keep fixes narrow; the playback service and queue are high-risk shared state.

# MYLO Scrobble Studio — Prototype

This is an isolated, browser-based interaction prototype. It does not change or replace Android production screens, playback, history, scrobbling, generation, or playlist services.

## Launch

From the repository root:

```bash
python3 -m http.server 4173
```

Open `http://localhost:4173/prototype/scrobble/` on desktop or Android. No build step or external service credentials are required. Artwork is loaded from Unsplash; without a network connection, artwork may be unavailable but the prototype remains usable.

## Explore

- Home preview: tap the floating Scrobble control to enter the workspace.
- Statistics: change periods, open ranked lists, inspect track/artist/album details, and explore listening history.
- Generate: choose a source and recipe, tune the mix, simulate generation, then save it to Playlists.
- Playlists: create playlists, open details, edit metadata, reorder with up/down controls, add/remove tracks, and delete with confirmation.
- Settings: change light/dark theme and glass/opaque surfaces; inspect preference groups and clear session data.
- Mini-player: persistent sample playback controls are shown inside the workspace.

## Prototype boundaries

- All music, play counts, listening timestamps, artwork references, and playback are sample/simulated.
- Playlist and preference mutations are in-memory for the current page session; they are not written to Mylo's database or synced.
- Generation is deterministic sample selection, not a production recommendation engine.
- The prototype does not connect to Last.fm, ListenBrainz, YouTube Music, Spotify, or Android Media3.
- The current pass prioritizes a coherent clickable vertical slice. Advanced chart drill-downs, true drag-and-drop, exhaustive recovery/error injection, full screen inventory, and screenshot QA remain follow-up work.

## Screen inventory in this pass

Home integration preview; Statistics overview; Top tracks/artists/albums; Listening history; Track/artist/album details; Generate source, recipe, configuration and result states; Playlist library, details, editor and add-tracks; Scrobble settings; contextual sheets and confirmations.

## Review checklist

1. Open Home and launch Scrobble.
2. Change the statistics period and open each top-list/detail path.
3. Complete a generation and save the result.
4. Create a playlist, add tracks, edit/reorder, then delete it.
5. Change theme and appearance settings.
6. Use the back control to leave nested screens.

This is a review prototype only. Do not treat it as production-ready or merge it into the Android feature without design and engineering review.

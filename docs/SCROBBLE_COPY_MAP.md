# Scrobble copy: literal to key map

One row per hardcoded user-facing literal in the scrobble UI. Swap each literal for `stringResource(R.string.<key>)`.
Format args: `%1$s` etc. take the same values the literal currently concatenates.
Rows marked **CHECK** need a decision or a code change beyond a plain swap (see notes at the end).

File abbreviations: `SC` = ScrobbleContent.kt, `STC` = ScrobbleStatisticsContent.kt, `STV` = ScrobbleStatisticsViewModel.kt, `GVM` = ScrobbleGenerateViewModel.kt, `PED` = PlaylistExportDialog.kt, `TC` = TrackConcentration.kt

| File | Literal | Key |
|---|---|---|
| STC | "Time period" | scrobble_period_selector_label |
| STC | "Your tracks" | scrobble_stats_heading_tracks |
| STC | "No listening history yet" | scrobble_stats_empty_title |
| STC | "No listening history" | scrobble_stats_empty_title_period |
| STC | "Play a few tracks, or connect Last.fm in Settings to bring in your history." | scrobble_stats_empty_body |
| STC | "No listening history for this period." | scrobble_stats_empty_body_period |
| STV | "Last.fm couldn't refresh. Showing saved statistics." | scrobble_stats_error_lastfm_saved |
| STV | "Last.fm couldn't refresh. Showing Mylo statistics." | scrobble_stats_error_lastfm_mylo |
| STV | "Statistics couldn't refresh. Please try again." | scrobble_stats_error_body |
| TC | "How your plays are distributed across your tracks." | scrobble_concentration_body |
| TC | "Top track" | scrobble_concentration_top |
| TC | "Next 2 tracks" | scrobble_concentration_next_two |
| TC | "Other tracks" | scrobble_concentration_other |
| SC | "Pick a vibe to build a playlist." | scrobble_generate_intro |
| SC | "Start from" | scrobble_gen_section_start |
| SC | "From your listening" | scrobble_gen_group_listening |
| SC | "Find something new" | scrobble_gen_group_new |
| SC | "By mood or genre" | scrobble_gen_group_mood |
| SC | "From your library" | scrobble_gen_group_library |
| SC | "Playlist size" | scrobble_gen_section_size |
| SC | "Optional exclusions" | scrobble_gen_section_exclusions |
| SC | "Seed track" | scrobble_gen_field_seed_track |
| SC | "Optional seed" | scrobble_gen_field_optional_seed |
| SC | "Artist" (label) | scrobble_gen_label_artist |
| SC | "Artist name" (placeholder) | scrobble_gen_field_artist |
| SC | "Mood or genre" (label) | scrobble_gen_field_mood |
| SC | "indie, late-night jazz, ambient…" (placeholder) | scrobble_gen_placeholder_mood |
| SC | "Period" (label) | scrobble_gen_field_period |
| SC | "Search for a track" | scrobble_gen_search_placeholder |
| SC | "No seed selected. Mylo will build a mix from your listening and recommendations." | scrobble_gen_seed_none |
| SC | "Use now playing · " + title | scrobble_gen_seed_now_playing |
| SC | "Using now playing · " + title | scrobble_gen_seed_using_now_playing |
| SC | "Add now playing as a seed · " + title | scrobble_gen_seed_add_now_playing |
| SC | "Remove now playing seed · " + title | scrobble_gen_seed_remove_now_playing |
| SC | "Selected · " + title + " — " + artist | scrobble_gen_seed_selected_summary |
| SC | "Tracks in saved playlists" | scrobble_gen_exclude_saved |
| SC | "Liked tracks" | scrobble_gen_exclude_liked |
| SC | "Library tracks" | scrobble_gen_exclude_library |
| SC | "Sign in to YouTube Music to exclude liked or library tracks." | scrobble_gen_sign_in_for_exclusions |
| SC | "Sign in to YouTube Music to use this" | scrobble_gen_sign_in_to_use |
| SC | "Only tracks you've actually heard are excluded by default. Add these filters if you want a stricter discovery mix." | scrobble_gen_exclude_hint |
| SC | "Works without Last.fm. Connect it for better picks." | scrobble_gen_no_lastfm |
| SC | "YouTube Music library tracks don't include a saved date in Mylo yet, so only All time can be applied." | scrobble_gen_library_no_date |
| SC | "Finding tracks that fit…" (fallback, lines 320 and 678) | scrobble_gen_loading_fallback |
| SC | "Generate playlist" | scrobble_generate_button |
| SC | "Couldn't search YouTube Music" (lines 194, 1814) | scrobble_gen_error_search |
| SC | Mode short labels: "Top", "Recent", "Similar Tracks", "Similar Artists", "Mix", "Recommendations", "Library", "Similar Playlist" | scrobble_mode_short_top, _recent, _similar_tracks, _similar_artists, _mix, _recommendations, _library, _similar_playlist |
| SC | Mode names (`modeTitle`): "My Top Tracks", "Recently Played", "Similar tracks", "Similar artists", "Mood or genre", "My Mix", "Made for You", "Never Heard", "My Library Mix", "Forgotten Favorites", "Top Artists Mix" | scrobble_mode_top, _recent, _similar_tracks, _similar_artists, _tag, _mix, _recommendations, _never_heard, _library, scrobble_mode_forgotten_favorites, scrobble_mode_top_artists_mix |
| SC | Mode promises (`modePromise`), one per mode | scrobble_mode_<mode>_promise (e.g. scrobble_mode_never_heard_promise) |
| SC | "Top Tracks" title for TAG_TOP ("Tag Top Tracks" today) | scrobble_mode_tag_top **CHECK**: spec wants "{tag} Top Tracks", use scrobble_gen_title_tag_top with the tag |
| SC | ReplayPeriod labels: "All time", "This year", "This month" | scrobble_period_all_time, scrobble_period_this_year, scrobble_period_this_month **CHECK**: pick one period set |
| SC | "This week" | replay_this_week |
| SC | "Couldn't save playlist order. Try again." (lines 1324, 1330) | scrobble_order_save_failed |
| SC | "Couldn't update playlist pin. Try again." | scrobble_pin_failed |
| SC | "Hide preview" / "Preview tracks" | scrobble_playlist_preview_hide / scrobble_playlist_preview |
| SC | "Find a playlist" | scrobble_search_placeholder |
| SC | "No playlists yet" | scrobble_playlists_empty_title |
| SC | "Create your first mix from the Generator." | scrobble_playlists_empty_body (text now says "Generate tab") |
| SC | "No matching playlists" + "Try another playlist name." | no_playlists_match (REUSE, arg = query) |
| SC | "Rename playlist" | rename_playlist |
| SC | "Choose a name for this playlist." | scrobble_rename_body |
| SC | "Playlist name" (placeholder) | playlist_name |
| SC | "Enter a playlist name" | scrobble_rename_empty |
| SC | "A playlist with that name already exists" | scrobble_rename_duplicate |
| SC | "Couldn't save playlist name. Try again." | scrobble_rename_failed |
| SC | "Delete \"${title}\"?" (dialog title) | scrobble_delete_named_title |
| SC | "This cannot be undone." | scrobble_delete_undo_note |
| SC | "Couldn't delete playlist. Check device storage and try again." | scrobble_delete_failed |
| SC | "Play" (menu) | scrobble_play |
| SC | "Pin" / "Unpin" (menu) | scrobble_pin / scrobble_unpin |
| SC | "Cover updated" | scrobble_cover_saved **CHECK** (see notes) |
| SC | "Couldn't save cover. Try again." | scrobble_cover_failed |
| SC | "Couldn't update cover" (fallback) | scrobble_cover_failed |
| SC | "Cover reset" | scrobble_cover_reset |
| SC | "Couldn't reset cover. Try again." | scrobble_cover_reset_failed |
| SC | "Add tracks" / "Done" | scrobble_add_tracks / scrobble_add_tracks_done |
| SC | "Search YouTube Music" | scrobble_search_youtube_placeholder |
| SC | "Add" / "Added" | scrobble_add_track / scrobble_added |
| SC | "This playlist is empty." | scrobble_playlist_empty |
| SC | "Couldn't save track order. Try again." | scrobble_track_order_failed |
| SC | "Couldn't remove track. Try again." | scrobble_track_remove_failed |
| SC | "Sort playlists" (content description) | scrobble_sort_playlists |
| SC | "Play ${playlist}" (content description) | scrobble_play_named |
| SC | "More options for ${playlist}" (content description) | scrobble_more_options |
| SC | "Back to playlists" (content description) | scrobble_back_to_playlists |
| GVM | "This song cannot start a mix. Choose a playable track." | scrobble_gen_error_song_no_mix |
| GVM | "A playlist is already being generated." | scrobble_gen_error_already_running |
| GVM | "Preparing your playlist…" | scrobble_gen_progress_preparing |
| GVM | "Playlist generation timed out. Check your connection and try again." | scrobble_gen_error_timeout |
| GVM | "Couldn't generate a playlist" (fallback) | scrobble_gen_error_generic |
| GVM | "Preparing a similar mix…" | scrobble_gen_progress_similar |
| GVM | "Similar playlist generation timed out. Check your connection and try again." | scrobble_gen_error_similar_timeout |
| GVM | "Couldn't create a similar playlist" (fallback) | scrobble_gen_error_similar_generic |
| GVM | Generated playlist titles: "My Top Tracks", "Recently Played", "My Mix", "Made for You", "Never Heard", "My Library Mix", "Forgotten Favorites", "Top Artists Mix" | same scrobble_mode_* keys as SC mode names |
| GVM | Default title "My Mix" | scrobble_mode_mix |
| PED | "Couldn't open the selected file" | scrobble_export_file_failed |
| PED | "CSV saved" / "M3U saved" | scrobble_export_csv_saved / scrobble_export_m3u_saved |
| PED | "Couldn't save CSV" / "Couldn't save M3U" | scrobble_export_csv_failed / scrobble_export_m3u_failed |
| PED | "Couldn't prepare the export file" | scrobble_export_prepare_failed |
| PED | "Share playlist" (chooser title) | scrobble_share_chooser_title |
| PED | "Ready to share" | scrobble_export_ready |
| PED | "Couldn't share playlist" | scrobble_export_share_failed |
| PED | "CSV" / "M3U" (format button labels) | scrobble_export_format_csv / scrobble_export_format_m3u |

## Notes for the UI pass

1. **Cover result color.** `SC` (about line 1961) compares the message text to `"Cover updated"` and `"Cover reset"`, and line 1969 checks `startsWith("Couldn't")`. After the swap these comparisons will fail. Compare against a state enum or a flag, not the string.
2. **Period labels.** Pick one set: `replay_period_*` or `scrobble_period_*` (spec open item 3). Both sets are in strings.xml for now; delete the one not used.
3. **TAG_TOP title.** `scrobble_mode_tag_top` reads "Tag Top Tracks" in strings.xml. The spec wants "{tag} Top Tracks", which is `scrobble_gen_title_tag_top` with the tag as `%1$s`. Use that for the generated title. For the card name, decide with design.
4. **Mode short labels** (`scrobble_mode_short_*`) are new keys. They are chip or menu labels. Confirm with design that they are shown.
5. **Stats error copy.** `scrobble_stats_error_body` replaces "Statistics couldn't refresh. Please try again." Wording changed per the spec.
6. **Exports.** Spec open item 4 (Save and Share pair vs format keys) is still open. Both sets are in strings.xml. `scrobble_save_*` and `scrobble_share_*` for CSV and M3U are unused until that is decided.
7. **Plurals.** The spec does not ask for plurals. `scrobble_insight_unique` ("You played %1$d different tracks.") will read "1 different tracks" for a count of 1. Decide whether to use `<plurals>` before shipping.
8. **Dialog title.** The delete dialog title in code is `"Delete \"${title}\"?"`. The spec title is "Delete playlist" (`delete_playlist`). Pick one. `scrobble_delete_named_title` is the code-matching option.

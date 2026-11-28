package com.music.bitchord

import com.music.bitchord.ui.components.SUPPORTED_LANGUAGES
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every language the app offers must translate every key in values/strings.xml. A missing key
 * doesn't crash, it silently falls back to English mid-screen, so this catches it at test time.
 */
@RunWith(Parameterized::class)
class LocaleStringsTest(private val tag: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun locales(): List<String> = SUPPORTED_LANGUAGES.map { it.tag }.filter { it != "en" }

        private val resDir: File by lazy {
            val projectRoot = File(".").canonicalFile
            if (File(projectRoot, "app/src/main/res").exists()) {
                File(projectRoot, "app/src/main/res")
            } else {
                File(projectRoot, "src/main/res")
            }
        }

        private fun parse(file: File): Document =
            DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

        /** Keys that need translating: `translatable="false"` entries are excluded. */
        private fun translatableKeys(doc: Document): Set<String> {
            val keys = mutableSetOf<String>()
            for (tagName in listOf("string", "plurals", "string-array")) {
                val nodes = doc.getElementsByTagName(tagName)
                for (i in 0 until nodes.length) {
                    val element = nodes.item(i) as Element
                    if (element.getAttribute("translatable") == "false") continue
                    val name = element.getAttribute("name")
                    if (name.isNotEmpty()) keys.add(name)
                }
            }
            return keys
        }

        /**
         * Keys added to values/strings.xml that no locale translates yet. They fall back to
         * English until translated; remove each entry here when its translations land.
         */
        internal val AWAITING_TRANSLATION = setOf(
            "about_gpl_fork_credit",
            "scrobble_mode_forgotten_favorites",
            "scrobble_mode_tag_top",
            "scrobble_mode_top_artists_mix",
            "scrobble_concentration_body",
            "scrobble_period_all_time",
            "scrobble_period_this_year",
            "scrobble_period_this_month",
            "scrobble_insight_hours",
            "scrobble_insight_top_artist",
            "scrobble_insight_top_track",
            "scrobble_insight_concentration",
            "scrobble_insight_unique",
            "scrobble_insight_avg",
            "scrobble_insight_busiest_day",
            "scrobble_stats_error_body",
            "scrobble_stats_error_lastfm_saved",
            "scrobble_stats_error_lastfm_mylo",
            "scrobble_stats_period_week",
            "scrobble_stats_period_month",
            "scrobble_stats_period_quarter",
            "scrobble_stats_period_half_year",
            "scrobble_stats_period_year",
            "scrobble_stats_period_overall",
            "scrobble_stats_scrobbles",
            "scrobble_stats_window_week",
            "scrobble_stats_window_month",
            "scrobble_stats_window_quarter",
            "scrobble_stats_window_half_year",
            "scrobble_stats_window_year",
            "scrobble_stats_window_overall",
            "scrobble_stats_window_overall_since",
            "scrobble_stats_top_artists",
            "scrobble_stats_top_tracks",
            "scrobble_stats_top_albums",
            "scrobble_stats_list_recent",
            "scrobble_stats_list_change",
            "scrobble_stats_yesterday",
            "scrobble_stats_show_dashboard",
            "scrobble_stats_load_more_failed",
            "scrobble_stats_recent_empty",
            "scrobble_stats_list_period_empty",
            "scrobble_stats_collapsed_caption",
            "scrobble_stats_plays_unit",
            "scrobble_stats_profile_since",
            "scrobble_stats_profile",
            "scrobble_stats_open_profile",
            "scrobble_stats_now_playing",
            "scrobble_stats_activity",
            "scrobble_stats_busiest",
            "scrobble_stats_today",
            "scrobble_stats_activity_description",
            "scrobble_stats_vs_previous_week",
            "scrobble_stats_vs_previous_month",
            "scrobble_stats_vs_previous_quarter",
            "scrobble_stats_vs_previous_half_year",
            "scrobble_stats_vs_previous_year",
            "scrobble_stats_change_up",
            "scrobble_stats_change_down",
            "scrobble_stats_change_flat",
            "scrobble_stats_per_day",
            "scrobble_stats_metric_artists",
            "scrobble_stats_metric_albums",
            "scrobble_stats_metric_tracks",
            "scrobble_stats_connect_title",
            "scrobble_stats_connect_body",
            "scrobble_stats_period_empty_title",
            "scrobble_stats_period_empty_body",
            "scrobble_stats_failed_title",
            "scrobble_stats_failed_body",
            "scrobble_stats_stale",
            "scrobble_stats_scrobble_count",
            "scrobble_stats_artist_count",
            "scrobble_stats_album_count",
            "scrobble_stats_track_count",
            "scrobble_generate_intro",
            "scrobble_gen_section_start",
            "scrobble_gen_group_listening",
            "scrobble_gen_group_new",
            "scrobble_gen_group_mood",
            "scrobble_gen_group_library",
            "scrobble_gen_section_size",
            "scrobble_gen_section_exclusions",
            "scrobble_gen_field_seed_track",
            "scrobble_gen_field_optional_seed",
            "scrobble_gen_field_artist",
            "scrobble_gen_field_mood",
            "scrobble_gen_search_placeholder",
            "scrobble_gen_seed_none",
            "scrobble_gen_seed_now_playing",
            "scrobble_gen_seed_remove_now_playing",
            "scrobble_gen_exclude_saved",
            "scrobble_gen_exclude_liked",
            "scrobble_gen_exclude_library",
            "scrobble_gen_exclude_hint",
            "scrobble_gen_sign_in_for_exclusions",
            "scrobble_gen_no_lastfm",
            "scrobble_gen_library_no_date",
            "scrobble_gen_fewer",
            "scrobble_gen_more",
            "scrobble_generate_button",
            "scrobble_gen_loading_fallback",
            "scrobble_gen_progress_replay",
            "scrobble_gen_progress_recent",
            "scrobble_gen_progress_tag",
            "scrobble_gen_progress_similar_artist",
            "scrobble_gen_progress_artists_loaded",
            "scrobble_gen_progress_library",
            "scrobble_gen_progress_forgotten",
            "scrobble_gen_error_no_tracks",
            "scrobble_gen_error_search",
            "scrobble_gen_error_seed",
            "scrobble_gen_error_artist",
            "scrobble_gen_error_tag",
            "scrobble_playlist_preview",
            "scrobble_playlist_preview_hide",
            "scrobble_add_tracks",
            "scrobble_add_tracks_done",
            "scrobble_playlists_empty_title",
            "scrobble_playlists_empty_body",
            "scrobble_search_placeholder",
            "scrobble_play",
            "scrobble_pin",
            "scrobble_unpin",
            "scrobble_order_save_failed",
            "scrobble_pin_failed",
            "scrobble_delete_failed",
            "scrobble_cover_saved",
            "scrobble_cover_failed",
            "scrobble_rename_empty",
            "scrobble_rename_failed",
            "scrobble_export_ready",
            "scrobble_export_csv_failed",
            "scrobble_export_m3u_failed",
            "scrobble_export_share_failed",
            "scrobble_share_chooser_title",
            "scrobble_export_file_failed",
            "scrobble_rank_row",
            "scrobble_track_row",
            "scrobble_mode_top",
            "scrobble_mode_recent",
            "scrobble_mode_similar_tracks",
            "scrobble_mode_similar_artists",
            "scrobble_mode_tag",
            "scrobble_mode_mix",
            "scrobble_mode_recommendations",
            "scrobble_mode_never_heard",
            "scrobble_mode_library",
            "scrobble_mode_top_promise",
            "scrobble_mode_recent_promise",
            "scrobble_mode_similar_tracks_promise",
            "scrobble_mode_similar_artists_promise",
            "scrobble_mode_tag_promise",
            "scrobble_mode_mix_promise",
            "scrobble_mode_recommendations_promise",
            "scrobble_mode_never_heard_promise",
            "scrobble_mode_library_promise",
            "scrobble_mode_forgotten_favorites_promise",
            "scrobble_mode_tag_top_promise",
            "scrobble_mode_top_artists_mix_promise",
            "scrobble_gen_title_similar_track",
            "scrobble_gen_title_similar_artist",
            "scrobble_gen_title_tag",
            "scrobble_gen_title_tag_top",
            "scrobble_rename_body",
            "scrobble_delete_undo_note",
            "scrobble_sort_playlists",
            "scrobble_play_named",
            "scrobble_more_options",
            "scrobble_back_to_playlists",
            "scrobble_gen_sign_in_to_use",
            "scrobble_gen_label_artist",
            "scrobble_gen_field_period",
            "scrobble_gen_placeholder_mood",
            "scrobble_gen_seed_add_now_playing",
            "scrobble_gen_seed_using_now_playing",
            "scrobble_gen_seed_selected_summary",
            "scrobble_gen_error_already_running",
            "scrobble_gen_error_song_no_mix",
            "scrobble_gen_error_timeout",
            "scrobble_gen_error_generic",
            "scrobble_gen_progress_preparing",
            "scrobble_gen_progress_similar",
            "scrobble_gen_error_similar_timeout",
            "scrobble_gen_error_similar_generic",
            "scrobble_mode_short_top",
            "scrobble_mode_short_recent",
            "scrobble_mode_short_similar_tracks",
            "scrobble_mode_short_similar_artists",
            "scrobble_mode_short_mix",
            "scrobble_mode_short_recommendations",
            "scrobble_mode_short_library",
            "scrobble_mode_short_similar_playlist",
            "scrobble_rename_duplicate",
            "scrobble_delete_named_title",
            "scrobble_cover_reset",
            "scrobble_cover_reset_failed",
            "scrobble_add_track",
            "scrobble_added",
            "scrobble_playlist_empty",
            "scrobble_track_order_failed",
            "scrobble_track_remove_failed",
            "scrobble_search_youtube_placeholder",
            "scrobble_concentration_top",
            "scrobble_concentration_next_two",
            "scrobble_concentration_other",
            "scrobble_export_csv_saved",
            "scrobble_export_m3u_saved",
            "scrobble_export_prepare_failed",
            "scrobble_export_format_csv",
            "scrobble_export_format_m3u",
            "scrobble_stats_listening_time",
            "scrobble_stats_heading_tracks",
            "scrobble_stats_sentence_week",
            "scrobble_stats_sentence_month",
            "scrobble_stats_sentence_year",
            "scrobble_stats_sentence_all_time",
            "scrobble_stats_variety",
            "scrobble_period_selector_label",
            "scrobble_stats_empty_title",
            "scrobble_stats_empty_body",
            "scrobble_stats_empty_title_period",
            "scrobble_stats_empty_body_period",
        )

        private val baseKeys: Set<String> by lazy {
            translatableKeys(parse(File(resDir, "values/strings.xml")))
        }

        /**
         * App language tags are BCP-47 (`zh-CN` / `zh-TW`) while Android
         * resource directories use `-r` regions (`values-zh`, `values-zh-rTW`).
         * Mirrors what Android resolves at runtime: Simplified is the bare
         * `zh` catalogue, Traditional is `zh-rTW`.
         */
        internal fun tagToQualifier(tag: String): String = when (tag) {
            "zh-CN" -> "zh"
            "zh-TW" -> "zh-rTW"
            else -> tag
        }
    }

    @Test
    fun strings_containsAllBaseKeys() {
        val qualifier = tagToQualifier(tag)
        val localeFile = File(resDir, "values-$qualifier/strings.xml")
        assertTrue("values-$qualifier/strings.xml must exist (for language tag $tag)", localeFile.exists())

        val missingKeys = baseKeys - translatableKeys(parse(localeFile)) - AWAITING_TRANSLATION
        assertTrue(
            "values-$qualifier/strings.xml (language $tag) is missing ${missingKeys.size} keys from values/strings.xml: $missingKeys",
            missingKeys.isEmpty(),
        )
    }
}

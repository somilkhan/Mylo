package com.music.bitchord.ui.screens

/**
 * Direct port of LastWave PlaylistNamer (v4). Generated playlists get a
 * distinctive 1–3 word title, while uniqueness is checked case-insensitively
 * against the user's saved playlist names. Mode context stays in the subtitle.
 */
internal object ScrobblePlaylistNamer {
    private val adjectives = listOf(
        "Crimson", "Violet", "Amber", "Emerald", "Sapphire", "Scarlet", "Ivory", "Obsidian",
        "Cobalt", "Copper", "Golden", "Silver", "Jade", "Ruby", "Indigo", "Pearl",
        "Electric", "Radiant", "Frozen", "Stormy", "Misty", "Blazing", "Frosted", "Luminous",
        "Fading", "Glowing", "Shimmering", "Windswept", "Sunlit", "Moonlit", "Hazy", "Rainswept",
        "Silent", "Restless", "Wild", "Faded", "Quiet", "Wistful", "Feral", "Serene",
        "Fractured", "Wandering", "Distant", "Hollow", "Tender", "Bold", "Gentle", "Dreamy",
        "Midnight", "Lunar", "Solar", "Arctic", "Celestial", "Nocturnal", "Endless", "Ancient",
        "Eternal", "Timeless", "Drifting", "Hidden", "Velvet", "Neon", "Glass", "Forgotten",
    )

    private val nouns = listOf(
        "Horizon", "Orbit", "Nebula", "Eclipse", "Comet", "Nova", "Zenith", "Meridian",
        "Solstice", "Constellation", "Cosmos", "Galaxy", "Halo", "Aurora", "Starlight", "Satellite",
        "Tides", "Storm", "Tempest", "Cascade", "Glacier", "Canyon", "Meadow", "Grove",
        "Summit", "Valley", "Wildfire", "Monsoon", "Frost", "Ember", "Bloom", "Thicket",
        "Harbor", "Lagoon", "Prairie", "Oasis", "Phoenix", "Oracle", "Siren", "Valkyrie",
        "Elysium", "Odyssey", "Atlas", "Titan", "Muse", "Chimera", "Echo", "Echoes",
        "Mirage", "Dreams", "Reverie", "Pulse", "Drift", "Wanderlust", "Shadows", "Sparks",
        "Voyage", "Whisper", "Serenade", "Solitude", "Skyline", "Twilight", "Daybreak", "Sanctuary",
        "Labyrinth", "Wilderness",
    )

    private fun composeOnce(): String {
        val roll = Math.random()
        return when {
            roll < 0.12 -> nouns.random()
            roll < 0.90 -> "${adjectives.random()} ${nouns.random()}"
            else -> {
                val first = adjectives.random()
                var second = adjectives.random()
                if (second == first) second = adjectives.random()
                "$first $second ${nouns.random()}"
            }
        }
    }

    private fun composeThreeWord(): String {
        val first = adjectives.random()
        var second = adjectives.random()
        if (second == first) second = adjectives.random()
        return "$first $second ${nouns.random()}"
    }

    /** Retries the same 80 + 200 attempts used by LastWave; never adds numeric suffixes. */
    fun generateUniqueName(existingTitles: Collection<String>): String {
        val taken = existingTitles.map { it.trim().lowercase() }.toHashSet()
        var candidate = ""
        repeat(80) {
            candidate = composeOnce()
            if (candidate.lowercase() !in taken) return candidate
        }
        repeat(200) {
            candidate = composeThreeWord()
            if (candidate.lowercase() !in taken) return candidate
        }
        return candidate
    }

    fun subtitleFor(mode: String, tag: String? = null, seedTitle: String? = null, seedArtist: String? = null): String =
        when (mode) {
            "tag" -> tag?.takeIf(String::isNotBlank)?.let {
                "Genre Mix • ${it.split(" ").joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }}"
            } ?: "Genre Mix"
            "recent" -> "Recent Tracks"
            "top", "library" -> "Top Tracks"
            "mix" -> "My Mix"
            "recommendations" -> "Recommendations"
            "never-heard" -> "Never-Heard Mix"
            "similar-tracks", "start-mix" -> seedTitle?.takeIf(String::isNotBlank)?.let { "Track Mix • $it" } ?: "Track Mix"
            "similar-artists" -> seedArtist?.takeIf(String::isNotBlank)?.let { "Artist Mix • $it" } ?: "Artist Mix"
            "discover" -> "Discover Feed"
            else -> "Mix"
        }
}

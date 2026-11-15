package com.music.bitchord.ui.screens.scrobble

/**
 * Direct adaptation of LastWave's PlaylistNamer. Generated mixes share one
 * naming bank so modes do not keep producing the same generic playlist title.
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
        "Voyage", "Whisper", "Serenade", "Solitude", "Skyline", "Twilight", "Daybreak",
        "Sanctuary", "Labyrinth", "Wilderness",
    )

    private fun composeOnce(): String {
        val chance = Math.random()
        return when {
            chance < 0.12 -> nouns.random()
            chance < 0.90 -> "${adjectives.random()} ${nouns.random()}"
            else -> composeThreeWord()
        }
    }

    private fun composeThreeWord(): String {
        val first = adjectives.random()
        var second = adjectives.random()
        if (second == first) second = adjectives.random()
        return "$first $second ${nouns.random()}"
    }

    /** Unique against trimmed, case-insensitive existing playlist titles. */
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

    /** Port of LastWave's GenerateMode.storageValue subtitle mapping. */
    fun subtitleFor(
        mode: String,
        tagInput: String? = null,
        seedTrackName: String? = null,
        seedArtistInput: String? = null,
    ): String = when (mode) {
        "tag" -> if (!tagInput.isNullOrBlank()) {
            "Genre Mix • " + tagInput.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar(Char::uppercase)
            }
        } else "Genre Mix"
        "recent" -> "Recent Tracks"
        "top", "library" -> "Top Tracks"
        "mix" -> "My Mix"
        "recommendations" -> "Recommendations"
        "never-heard" -> "Never-Heard Mix"
        "similar-tracks", "start-mix" -> if (!seedTrackName.isNullOrBlank()) {
            "Track Mix • $seedTrackName"
        } else "Track Mix"
        "similar-artists" -> if (!seedArtistInput.isNullOrBlank()) {
            "Artist Mix • $seedArtistInput"
        } else "Artist Mix"
        "discover" -> "Discover Feed"
        else -> "Mix"
    }
}

package com.music.bitchord.data.generator

import com.music.bitchord.auth.GoogleAccountSession
import java.security.MessageDigest

/** Authenticated identity for a background fetch; never retains the cookie itself. */
internal data class ScrobbleSessionIdentity(
    val accountId: String,
    val profileId: String?,
    val cookieFingerprint: String,
    val pageId: String?,
    val dataSyncId: String?,
    val authUser: String?,
)

internal fun GoogleAccountSession.scrobbleIdentity(selectedProfileId: String?): ScrobbleSessionIdentity {
    val effectiveProfileId = selectedProfileId ?: activeProfileId
    val profile = profiles.firstOrNull { it.profileId == effectiveProfileId }
    val fingerprint = MessageDigest.getInstance("SHA-256")
        .digest(cookie.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return ScrobbleSessionIdentity(
        accountId = accountId,
        profileId = effectiveProfileId,
        cookieFingerprint = fingerprint,
        pageId = profile?.pageId,
        dataSyncId = profile?.dataSyncId,
        authUser = profile?.authUser,
    )
}

/**
 * Preferences key for the last successful history import for this account/profile.
 *
 * Cookie fingerprints intentionally do not participate: rotating a valid session
 * must fence in-flight requests, but it must not restart a complete history import.
 * Stable account/channel identifiers are hashed so raw account metadata is never
 * written into a preference key.
 */
internal fun ScrobbleSessionIdentity.historySyncCursorPreferenceKey(): String {
    val stableIdentity = listOf(
        accountId,
        profileId.orEmpty(),
        pageId.orEmpty(),
        dataSyncId.orEmpty(),
        authUser.orEmpty(),
    ).joinToString(separator = "\u0000")
    val fingerprint = MessageDigest.getInstance("SHA-256")
        .digest(stableIdentity.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    return "youtube_history_sync_cursor_$fingerprint"
}

/** Recognize Innertube authentication rejection through wrapped transport errors. */
internal fun Throwable.isYouTubeAuthenticationFailure(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        val message = current.message.orEmpty().lowercase()
        if (
            Regex("""\b(401|403)\b""").containsMatchIn(message) ||
            message.contains("unauthorized") ||
            message.contains("forbidden") ||
            message.contains("authentication required") ||
            message.contains("unauthenticated") ||
            message.contains("invalid session") ||
            message.contains("session expired") ||
            message.contains("login_required") ||
            message.contains("login required") ||
            message.contains("sign in to continue")
        ) return true
        current = current.cause
    }
    return false
}

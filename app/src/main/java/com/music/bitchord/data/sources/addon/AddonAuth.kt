package com.music.bitchord.data.sources.addon

import android.content.Context

/**
 * Stub for MYLO. BitChord's addon attestation is not used here: the auth
 * service only trusts BitChord's own signing key, so verification can never
 * succeed for MYLO. Addons that ask for it get a clear error instead.
 *
 * Kept as a stub, not deleted, because the rest of the sources code still
 * refers to these names. Remove the callers and this file together later.
 */
object AddonAuth {
    const val TOKEN_HEADER = "X-BitChord-Token"
    const val PROOF_HEADER = "X-BitChord-Proof"

    fun init(context: Context) {}

    /** Never refused in MYLO: there is no verification to back off from. */
    fun standingRefusal(): String? = null

    /** Always fails: this app cannot produce BitChord's verification. */
    suspend fun headersFor(host: String, rejectedToken: String? = null): Map<String, String> =
        throw AddonException("This addon needs BitChord verification, which MYLO doesn't support")

    fun hasTesterCode(): Boolean = false

    fun setTesterCode(raw: String?) {}

    fun normalizeTesterCode(raw: String?): String =
        raw.orEmpty().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

    fun rejectionMessage(code: String?): String =
        "This addon didn't accept BitChord's verification" + (code?.let { " ($it)" } ?: "")
}

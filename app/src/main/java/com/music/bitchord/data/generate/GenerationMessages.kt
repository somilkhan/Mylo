package com.music.bitchord.data.generate

import android.content.Context
import androidx.annotation.StringRes
import com.music.bitchord.R

/** Only deliberate, localized failures are safe to show; provider diagnostics stay out of the UI. */
internal class GenerationException(
    @param:StringRes val resource: Int,
    vararg val arguments: Any,
) : IllegalStateException() {
    fun localizedMessage(context: Context): String = context.getString(resource, *arguments)
}

internal fun Throwable.generationMessage(context: Context, @StringRes fallback: Int): String =
    if (this is GenerationException) localizedMessage(context) else context.getString(fallback)

internal fun requireGeneration(condition: Boolean, @StringRes resource: Int, vararg arguments: Any) {
    if (!condition) throw GenerationException(resource, *arguments)
}

/** A failed mandatory exclusion read must never become an empty exclusion set. */
internal suspend fun <T> generationExclusionRead(block: suspend () -> T): T = try {
    block()
} catch (cancelled: kotlinx.coroutines.CancellationException) {
    throw cancelled
} catch (_: Exception) {
    throw GenerationException(R.string.scrobble_gen_error_exclusions_unavailable)
}

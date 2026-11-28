package com.music.bitchord.data.generate

import android.content.Context
import com.music.bitchord.R
import com.music.bitchord.auth.SignedIn
import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.scrobbling.LastFM
import com.music.bitchord.data.settings.AppSettings

/** Not a data class: auth material must never appear in generated toString/log output. */
internal class GenerationAccountSnapshot(context: Context) {
    private val appContext = context.applicationContext
    private val username = AppSettings.lastfmUsername.value.trim()
    private val enabled = AppSettings.lastfmEnabled.value
    private val lastFmConfig = LastFM.currentConfig()
    private val signedIn = SignedIn.state(appContext).value
    private val cookie = Innertube.cookie

    /** Checked after discovery and immediately before handing off to durable playlist storage. */
    fun ensureUnchanged() {
        requireGeneration(
            username == AppSettings.lastfmUsername.value.trim() &&
                enabled == AppSettings.lastfmEnabled.value &&
                lastFmConfig == LastFM.currentConfig() &&
                signedIn == SignedIn.state(appContext).value &&
                cookie == Innertube.cookie,
            R.string.scrobble_gen_error_account_changed,
        )
    }
}

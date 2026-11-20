package com.music.bitchord.data

/**
 * Short-lived, single-key cache that lets concurrent history readers share one fetch.
 *
 * Holds the result for one account identity at a time. A read under a different key
 * is a miss, so an account or channel switch can never return the previous identity's
 * rows. Pure Kotlin with an injectable clock so it can be unit-tested.
 */
class HistoryShareCache<T>(
    private val windowMs: Long = 5_000L,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var key: String? = null
    private var storedAt = 0L
    private var value: T? = null

    @Synchronized
    fun get(key: String): T? {
        if (this.key != key) return null
        if (now() - storedAt >= windowMs) return null
        return value
    }

    @Synchronized
    fun put(key: String, result: T) {
        this.key = key
        this.storedAt = now()
        this.value = result
    }

    @Synchronized
    fun clear() {
        key = null
        storedAt = 0L
        value = null
    }
}

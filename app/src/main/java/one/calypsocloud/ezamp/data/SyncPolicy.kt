package one.calypsocloud.ezamp.data

/**
 * When an automatic sync is due, kept free of Android so it can be unit-tested on the JVM.
 * Manual SYNC never asks; it calls resync() directly.
 */
object SyncPolicy {

    /** Minimum gap between automatic syncs. */
    const val THROTTLE_MS = 15 * 60 * 1000L

    /** Due when there has never been a successful sync, or the last one is at least [throttleMs] old. */
    fun due(lastSyncMs: Long, nowMs: Long, throttleMs: Long = THROTTLE_MS): Boolean =
        lastSyncMs <= 0 || nowMs - lastSyncMs >= throttleMs
}

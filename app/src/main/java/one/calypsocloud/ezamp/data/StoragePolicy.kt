package one.calypsocloud.ezamp.data

/**
 * Storage cap arithmetic, kept free of Android so it can be unit-tested on the JVM.
 *
 * Policy: a cap refuses work, it never evicts. Deleting music to make room turns a full disk into
 * lost data; a refusal is only a message. `capMb <= 0` means unlimited.
 */
object StoragePolicy {

    const val MB = 1024L * 1024L

    fun capBytes(capMb: Int): Long = if (capMb <= 0) 0L else capMb * MB

    /** Committed bytes: only finished tracks count, so a half-downloaded file cannot fake room. */
    fun used(bytesOfDoneTracks: Iterable<Long>): Long = bytesOfDoneTracks.sum()

    /** True when there is no room left to start another album. */
    fun atCap(usedBytes: Long, capMb: Int): Boolean {
        val cap = capBytes(capMb)
        return cap > 0 && usedBytes >= cap
    }

    /**
     * Checked after each track lands. The cap is crossed mid-album rather than at the door, because
     * the size of a track is only known once it has been fetched.
     */
    fun overCap(usedBytes: Long, capMb: Int): Boolean {
        val cap = capBytes(capMb)
        return cap > 0 && usedBytes > cap
    }

    fun remainingBytes(usedBytes: Long, capMb: Int): Long {
        val cap = capBytes(capMb)
        return if (cap <= 0) Long.MAX_VALUE else (cap - usedBytes).coerceAtLeast(0L)
    }
}

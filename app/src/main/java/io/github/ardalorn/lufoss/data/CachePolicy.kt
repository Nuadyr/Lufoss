package io.github.ardalorn.lufoss.data

import java.util.Locale

/** Rules that keep the disk cache short-lived. Plain functions so they can be tested without Android. */
object CachePolicy {
    /**
     * Returns a Cache-Control value whose freshness is at most [maxAgeSeconds]. A shorter server value is kept,
     * a longer or missing one is replaced, and `no-store` is left alone so those responses are still never stored.
     */
    fun capCacheControl(header: String?, maxAgeSeconds: Long): String {
        val original = header.orEmpty()
        val parts = original.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.any { it.equals("no-store", ignoreCase = true) }) return original
        var age = maxAgeSeconds
        val kept = ArrayList<String>()
        for (part in parts) {
            when (part.substringBefore('=').trim().lowercase(Locale.ROOT)) {
                "max-age" -> part.substringAfter('=', "").trim().trim('"').toLongOrNull()?.let {
                    age = minOf(age, maxOf(it, 0L))
                }
                "s-maxage", "immutable" -> Unit
                else -> kept += part
            }
        }
        kept += "max-age=$age"
        return kept.joinToString(", ")
    }

    /** True when the last wipe is [ttlMs] or more in the past, or the clock has moved backwards. */
    fun isExpired(lastPurgeMs: Long, nowMs: Long, ttlMs: Long): Boolean =
        nowMs < lastPurgeMs || nowMs - lastPurgeMs >= ttlMs
}

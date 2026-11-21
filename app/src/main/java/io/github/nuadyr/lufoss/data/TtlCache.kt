package io.github.nuadyr.lufoss.data

import io.github.nuadyr.lufoss.Cfg

/** A small thread-safe cache where each answer expires: good answers last longer than empty ones. */
class TtlCache<V : Any>(
    private val maxEntries: Int = Cfg.CACHE_MAX_ENTRIES,
    private val trimTo: Int = Cfg.CACHE_TRIM_TO
) {
    private class Entry<T>(val value: T, val expiresAt: Long)

    private val map = LinkedHashMap<String, Entry<V>>()

    @Synchronized
    fun get(key: String): V? {
        val entry = map[key] ?: return null
        if (entry.expiresAt <= System.currentTimeMillis()) {
            map.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: String, value: V, ttlMs: Long) {
        map.remove(key)
        map[key] = Entry(value, System.currentTimeMillis() + ttlMs)
        if (map.size > maxEntries) {
            val now = System.currentTimeMillis()
            val expired = map.entries.iterator()
            while (expired.hasNext() && map.size > trimTo) {
                if (expired.next().value.expiresAt <= now) expired.remove()
            }
            val oldest = map.entries.iterator()
            while (oldest.hasNext() && map.size > trimTo) {
                oldest.next()
                oldest.remove()
            }
        }
    }
}

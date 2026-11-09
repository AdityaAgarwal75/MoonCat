package com.nuvio.tv.core.catalog

/** Small access-ordered cache. Count and UTF-16 payload weight both bound TV heap use. */
internal class CatalogResponseCache(
    private val maxBytes: Long = 4 * 1024 * 1024,
    private val maxEntries: Int = 48,
    private val ttlMs: Long = 15 * 60 * 1000,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private data class Entry(val value: String, val atMs: Long, val weight: Long)
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)
    private var weight = 0L

    @Synchronized fun get(key: String): String? {
        val entry = entries[key] ?: return null
        if (nowMs() - entry.atMs >= ttlMs) {
            entries.remove(key)
            weight -= entry.weight
            return null
        }
        return entry.value
    }

    @Synchronized fun put(key: String, value: String) {
        entries.remove(key)?.let { weight -= it.weight }
        val itemWeight = 2L * (key.length.toLong() + value.length)
        // One oversized response must not evict all of the useful home rows.
        if (itemWeight > maxBytes || maxEntries <= 0) return
        val now = nowMs()
        val expired = entries.iterator()
        while (expired.hasNext()) {
            val entry = expired.next().value
            if (now - entry.atMs >= ttlMs) {
                weight -= entry.weight
                expired.remove()
            }
        }
        entries[key] = Entry(value, now, itemWeight)
        weight += itemWeight
        while (entries.size > maxEntries || weight > maxBytes) {
            val iterator = entries.iterator()
            weight -= iterator.next().value.weight
            iterator.remove()
        }
    }
}

package com.nuvio.tv.core.image

/** Bounds speculative poster work and its history independently of visible image loads. */
internal class RevalidationRegistry(
    private val cooldownMs: Long,
    private val maxPending: Int = 16,
    private val maxHistory: Int = 512,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val pending = mutableSetOf<String>()
    private val completed = LinkedHashMap<String, Long>()

    init {
        require(cooldownMs >= 0 && maxPending > 0 && maxHistory > 0)
    }

    @Synchronized
    fun acquire(key: String): Boolean {
        if (key in pending || pending.size >= maxPending) return false
        val previous = completed[key]
        if (previous != null && nowMs() - previous < cooldownMs) return false
        pending.add(key)
        return true
    }

    @Synchronized
    fun complete(key: String) {
        if (!pending.remove(key)) return
        completed.remove(key)
        completed[key] = nowMs()
        while (completed.size > maxHistory) {
            val oldest = completed.keys.iterator()
            oldest.next()
            oldest.remove()
        }
    }
}

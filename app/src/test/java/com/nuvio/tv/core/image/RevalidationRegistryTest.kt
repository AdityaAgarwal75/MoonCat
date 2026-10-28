package com.nuvio.tv.core.image

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RevalidationRegistryTest {
    @Test
    fun `scroll bursts have bounded speculative work and duplicate posters coalesce`() {
        val registry = RevalidationRegistry(100, maxPending = 2)
        assertTrue(registry.acquire("a"))
        assertFalse(registry.acquire("a"))
        assertTrue(registry.acquire("b"))
        assertFalse(registry.acquire("c"))
        registry.complete("a")
        assertTrue(registry.acquire("c"))
    }

    @Test
    fun `stale posters cool down then become eligible again`() {
        var now = 0L
        val registry = RevalidationRegistry(100, nowMs = { now })
        assertTrue(registry.acquire("poster"))
        registry.complete("poster")
        now = 99
        assertFalse(registry.acquire("poster"))
        now = 100
        assertTrue(registry.acquire("poster"))
    }

    @Test
    fun `history evicts oldest completion without losing pending identities`() {
        val registry = RevalidationRegistry(100, maxPending = 2, maxHistory = 1, nowMs = { 0L })
        assertTrue(registry.acquire("pending"))
        assertTrue(registry.acquire("old"))
        registry.complete("old")
        assertTrue(registry.acquire("new"))
        registry.complete("new")
        assertFalse(registry.acquire("pending"))
        assertFalse(registry.acquire("new"))
        assertTrue(registry.acquire("old"))
    }
}

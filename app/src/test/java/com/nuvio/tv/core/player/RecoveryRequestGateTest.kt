package com.nuvio.tv.core.player

import org.junit.Assert.*
import org.junit.Test

class RecoveryRequestGateTest {
    @Test fun `only one recovery owns preparation at a time`() {
        val gate = RecoveryRequestGate()
        val ticket = gate.begin()!!
        assertNull(gate.begin())
        assertTrue(gate.isCurrent(ticket))
        gate.finish(ticket)
        assertNotNull(gate.begin())
    }

    @Test fun `old cleanup cannot release a newer recovery`() {
        val gate = RecoveryRequestGate()
        val old = gate.begin()!!
        gate.invalidate()
        val current = gate.begin()!!
        gate.finish(old)
        assertFalse(gate.isCurrent(old))
        assertTrue(gate.isCurrent(current))
        assertNull(gate.begin())
    }

    @Test fun `failure while preparing is retained for the owning recovery`() {
        val gate = RecoveryRequestGate()
        val ticket = gate.begin()!!
        gate.reportFailure("preparation failed")
        assertEquals("preparation failed", gate.takeFailure(ticket))
        assertNull(gate.takeFailure(ticket))
        gate.invalidate()
        val next = gate.begin()!!
        assertNull(gate.takeFailure(next))
    }
}

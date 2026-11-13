package com.nuvio.tv.core.player

/** Main-thread confined ownership of an asynchronous source transition.
 * Invalidating a request makes both its result and its finally block harmless.
 */
class RecoveryRequestGate {
    data class Ticket internal constructor(val generation: Long)
    private var generation = 0L
    private var owner: Ticket? = null
    private var failure: String? = null

    fun begin(): Ticket? {
        if (owner != null) return null
        failure = null
        return Ticket(++generation).also { owner = it }
    }

    fun isCurrent(ticket: Ticket): Boolean = owner == ticket

    fun reportFailure(message: String): Boolean {
        if (owner == null) return false
        failure = message
        return true
    }

    fun takeFailure(ticket: Ticket): String? {
        if (!isCurrent(ticket)) return null
        return failure.also { failure = null }
    }

    fun finish(ticket: Ticket) {
        if (isCurrent(ticket)) invalidate()
    }

    fun invalidate() {
        generation++
        owner = null
        failure = null
    }
}

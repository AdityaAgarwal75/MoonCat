package com.nuvio.tv.core.player

/** A failed startup can occur before the player's zero position receives the saved seek. */
internal fun sourceRecoveryPosition(
    currentPositionMs: Long,
    pendingSeekPositionMs: Long?,
    initialResumePositionMs: Long?
): Long = (pendingSeekPositionMs ?: initialResumePositionMs ?: currentPositionMs).coerceAtLeast(0L)

package com.nuvio.tv.core.player

/** A failed startup can occur before the player's zero position receives the saved seek. */
internal fun sourceRecoveryPosition(
    currentPositionMs: Long,
    pendingSeekPositionMs: Long?,
    initialResumePositionMs: Long?
): Long = (pendingSeekPositionMs ?: initialResumePositionMs ?: currentPositionMs).coerceAtLeast(0L)

/** A torrent may not have reached the saved-progress lookup yet. Let the new
 * source load that progress rather than forcing its temporary zero playhead. */
internal fun manualSourceSwitchPosition(
    currentPositionMs: Long,
    pendingSeekPositionMs: Long?,
    initialResumePositionMs: Long?,
    hasRenderedFirstFrame: Boolean,
    startFromBeginning: Boolean
): Long? = if (!hasRenderedFirstFrame && !startFromBeginning &&
    pendingSeekPositionMs == null && initialResumePositionMs == null) null
else sourceRecoveryPosition(currentPositionMs, pendingSeekPositionMs, initialResumePositionMs)

package com.nuvio.tv.core.mooncat

import com.nuvio.tv.data.local.PlayerSettings

/** Runtime limits; saved preferences are preserved for use on more capable devices. */
object FireTvPlaybackPolicy {
    fun constrained(totalRamBytes: Long, maxHeapBytes: Long): Boolean =
        totalRamBytes <= 0L || totalRamBytes < 1_300_000_000L || maxHeapBytes <= 256L * 1024 * 1024

    fun apply(settings: PlayerSettings, totalRamBytes: Long, maxHeapBytes: Long): PlayerSettings {
        val limited = constrained(totalRamBytes, maxHeapBytes)
        val buffer = settings.bufferSettings
        val startup = buffer.bufferForPlaybackMs.coerceIn(250, 5_000)
        val rebuffer = buffer.bufferForPlaybackAfterRebufferMs.coerceIn(startup, 10_000)
        val min = buffer.minBufferMs.coerceIn(rebuffer, 60_000)
        val max = buffer.maxBufferMs.coerceIn(min, 120_000)
        return settings.copy(
            allow4kWhenFast = settings.allow4kWhenFast && !limited,
            parallelNetworkEnabled = settings.parallelNetworkEnabled && !limited,
            nuvioPerformanceModeEnabled = settings.nuvioPerformanceModeEnabled && !limited,
            bufferBudgetManaged = settings.bufferBudgetManaged || limited,
            allowLargeTargetBuffer = settings.allowLargeTargetBuffer && !limited,
            preloadNextEpisodeSources = settings.preloadNextEpisodeSources && !limited,
            bufferSettings = buffer.copy(
                minBufferMs = min, maxBufferMs = max,
                bufferForPlaybackMs = startup, bufferForPlaybackAfterRebufferMs = rebuffer,
                backBufferDurationMs = if (limited) 0 else buffer.backBufferDurationMs.coerceIn(0, 15_000)
            )
        )
    }

    /** A large advertised app heap must not bypass a small physical-RAM limit. */
    fun managedBudgetMb(totalRamBytes: Long, maxHeapBytes: Long, defaultBudgetMb: Int): Int =
        if (constrained(totalRamBytes, maxHeapBytes)) defaultBudgetMb.coerceAtMost(heapBufferMb(maxHeapBytes))
        else defaultBudgetMb

    fun heapBufferMb(maxHeapBytes: Long): Int =
        (maxHeapBytes / (1024 * 1024) / 5).toInt().coerceIn(16, 48)
}

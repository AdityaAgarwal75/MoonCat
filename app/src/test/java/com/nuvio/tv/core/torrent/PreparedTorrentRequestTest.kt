package com.nuvio.tv.core.torrent

import org.junit.Assert.*
import org.junit.Test

class PreparedTorrentRequestTest {
    @Test fun `only the same episode file can claim the prepared route`() {
        val request = PreparedTorrentRequest("hash", 3, "episode3.mkv")
        assertTrue(request.canReuse(request, 100, 200))
        assertFalse(request.canReuse(request.copy(fileIndex = 4), 100, 200))
        assertFalse(request.canReuse(request.copy(fileIndex = null), 100, 200))
        assertFalse(request.canReuse(request.copy(hash = "other"), 100, 200))
        assertFalse(request.canReuse(request.copy(filename = "episode4.mkv"), 100, 200))
    }
    @Test fun `expired routes are rejected even if cleanup has not run`() {
        val request = PreparedTorrentRequest("hash", null, null)
        assertFalse(request.canReuse(request, 100, 100 + PreparedTorrentRequest.TTL_MS))
        assertFalse(request.canReuse(request, 100, 99))
    }
}

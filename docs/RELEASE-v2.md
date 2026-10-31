# MoonCat v2.0.0-beta.7

Restores enabled configured Stremio stream addons alongside Torrentio, while retaining the TorrentsDB exclusion. No new addon or paid service is automatically installed. Configured Comet/AIOStreams and other compatible HTTPS manifests can now contribute streams.

Source probes reserve room for both HTTP and torrents and for audio-language fallbacks. Many preferred-language entries no longer crowd every alternative out of the first race. Probe concurrency stays bounded for the 1 GB Fire Stick.

Verified preferred-language 1080p starts without the extra quality grace delay. Other ready sources get a short bounded grace for a better verified choice. HTTP selection prefers HD with measured headroom over an unnecessarily faster lower resolution. If torrent readiness probes fail, normal-player fallback uses advertised seed availability instead of quality alone.

Fixes native player-resolved sources such as YouTube being silently discarded by the probe coordinator. Existing source failover, no-4K visibility and reported file-size limit of 4 GB remain in place. Playback timing depends on available sources, peers and the network; this beta does not guarantee a first frame within 10 seconds.

Built on Beta 6 with the v2 package and signing identity. The Android installer must confirm the update. Physical Fire TV testing is still needed.

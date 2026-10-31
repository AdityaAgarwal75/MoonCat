# MoonCat v2.0.0-beta.9

Beta 9 builds on the signed Beta 8 playback and warmup changes. It replaces the preplay HTTP/torrent readiness race with immediate metadata selection when a source appears. The app prefers the configured Hindi/English audio, 1080p, and a reported size near 2 GB, up to 4 GB. Source names, seed counts, and file sizes are hints, not measured speed or actual viewer counts. The player still has to connect, prepare torrent metadata and buffer video. Manual source switching and existing recovery remain available.

ThePirateBay+ is installed alongside Torrentio for new and existing profiles and queried through the existing parallel addon pipeline. Its public manifest returned HTTP 502 during development; the app has a bootstrap descriptor to keep the provider visible, but availability is not guaranteed. Users can disable or uninstall it in Addons. TorrentsDB remains excluded.

The selected tuxedo cat has a vivid square icon and a cat-forward Fire TV banner. A separate cozy living-room illustration replaces the startup splash; profile, mark and poster assets match it. The existing alternative theme icons and some legacy wordmarks are still older art and need a later consistency pass.

The earlier detail warmup still runs when enabled. It may send stream requests before Play, and torrents still require engine setup. No physical Fire TV timing measurement or instant-play guarantee is claimed. Compare Beta 8 and Beta 9 on the same title, profile and network; measure Play to first frame and failures, including dead or mislabelled sources. If the fast path fails too often, restore a short targeted validation only for failed providers.

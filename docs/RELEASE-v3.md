# MoonCat 3.0.0-alpha.4

- Autoplay rejects blank stream links and can still use the torrent route when an addon supplies an info hash. Manual fallback remains available when a source fails to resolve.
- Autoplay preserves a playable saved release group for the next episode. A saved torrent reporting zero seeders yields to a viable alternative. Custom regex rules use the pattern exactly as written.
- Torrentio enters the bounded discovery queue first. Results from other configured providers still appear as they arrive. Discovery reports when the returned files are 4K or over 6 GB and does not restore files that later prove oversized.
- The source screen ranks provider results once for autoplay and applies badges in batches. Badge updates keep the latest source metadata and avoid rebuilding the whole list for every few results.
- Version code 30003 retains package `dev.mooncat.tv.v2` and the trusted signing identity for in-place updates.

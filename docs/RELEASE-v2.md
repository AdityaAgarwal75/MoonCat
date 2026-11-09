# MoonCat 2.0.0-beta.12

Playback reliability and TV performance update, based on the published Beta 11 source.

- Autoplay compares fast provider responses for up to 250 ms. If the preferred audio language is missing, it allows up to 750 ms total before using the fallback; a completed search returns immediately. It does not run media downloads or competing torrent engines before Play.
- At the same audio language and resolution, direct/resolver routes come before unprepared torrents. Torrents reporting at least five seeds, or no seed count, rank ahead of torrents reporting only one to four or zero seeds. The 3–3.5 GB preference remains within comparable availability tiers. Counts are provider claims, not speed measurements.
- Source recovery follows the same ranking and 4 GB cap. A VOD source whose buffer stops advancing for 15 seconds can trigger recovery at the current position, including stalls before READY. Successful playback and manually paused playback are left alone. Automatic recovery remains limited to four attempts per title/episode.
- Next-episode binge groups survive metadata sorting without overriding the preferred audio language. Torrent “Start from beginning” now honors that choice. Recovery before the first frame also preserves a saved resume position that the failed player had not applied yet.
- Parsing for metadata sorting is performed once per candidate. Concurrent addon/availability requests are capped across searches. Empty filtered provider groups are not cached as playable hits.
- Discovery catalogue memory uses an LRU cache with a 4 MiB payload budget and 48-entry limit, rather than clearing all rows when a count threshold is exceeded.
- Update downloads cancel their HTTP requests, reject incomplete/mismatched file sizes, clean partial files, throttle progress callbacks, and prevent concurrent downloads. APK package, version and signing-certificate checks remain enforced.

## Installation

Use the signed `MoonCat-v2.0.0-beta.12-FireTV.apk` to update the existing v2 app (`dev.mooncat.tv.v2`). Beta installations use the **Beta** update channel. Preview APKs are separate apps and are not compatible replacements.

Provider availability, swarm health and home-network speed still determine startup and buffering. This release does not promise instant playback. Physical Fire TV testing remains necessary for measured Play-to-first-frame time, remote focus, sustained streaming, decoder support and real memory use. See `docs/AUDIT-beta12.md` for scope, research and validation.

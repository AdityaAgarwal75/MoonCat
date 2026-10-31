# Source research and Beta 7 changes — 31 October 2026

Research based on upstream repository documentation and the linked implementation files, not upstream performance claims or unverified host availability.

| Project | Sources and selection | MoonCat decision |
| --- | --- | --- |
| [NuvioTV](https://github.com/NuvioMedia/NuvioTV) | Bring-your-own sources; Kotlin/Compose/Media3. MoonCat is already derived from this project. | Improve the existing backend rather than replacing the app. |
| [Lumera](https://github.com/LumeraD3v/Lumera/blob/main/app/src/main/java/com/lumera/app/data/stream/StreamSortingService.kt) | Installed Stremio addons; quality/size filtering, seeds and addon priority. [SourceSelectionStore](https://github.com/LumeraD3v/Lumera/blob/main/app/src/main/java/com/lumera/app/data/player/SourceSelectionStore.kt) remembers a selection scoped to playback ID. | Restore configured addon support; prefer viable HD over needlessly faster SD/720p. Existing MoonCat cache/binge-group reuse stays intact. |
| [AIOStreams](https://github.com/Viren070/AIOStreams) | Aggregates configured addons/services; deduplicates by hash/file, filters and sorts; backup source groups. | Reserve probe slots across transport and language; retain request-aware deduplication so distinct auth headers/CDNs survive. Install a real configured instance through Addons if desired. |
| [Comet](https://github.com/g0ldyy/comet) | Metadata scrapers include Torrentio, Zilean, MediaFusion, Prowlarr/Jackett and others. SQLite/Postgres cache, background scraping and torrent ranking; direct torrents and configured debrid services. | Support its configured Stremio manifest. Do not embed a Python scraper/database stack in a 1 GB Fire Stick. No public endpoint's playback availability is established by this research. |
| [Stremio addon SDK](https://github.com/Stremio/stremio-addon-sdk) | Manifest declares resources/types/ID prefixes; streams distinguish HTTP, torrents, YouTube and external links. | Hand native server/YouTube/debrid resolution to the player instead of discarding those routes for lacking an HTTP sample URL. |
| [SamSUCKS](https://github.com/rfaile313/samsucks) / [Jellyfin Android TV](https://github.com/jellyfin/jellyfin-androidtv) | Clients for an existing personal media server. Samsung's decoder is Tizen-specific. | No drop-in backend or new public source catalogue. MoonCat already has personal-server integration. |

## Beta 7 implementation

- Query enabled configured HTTPS stream addons rather than silently allowing only Torrentio. Keep TorrentsDB excluded, per the household's earlier explicit preference. No new manifest, subscription, or hosted service is installed automatically.
- Reserve initial probe slots for direct HTTP and torrents, including fallback audio languages, instead of using all eight slots for one category. Torrent preloads remain capped at four; HTTP samples stay bounded.
- Keep a fallback-language torrent in the native four-slot race when many preferred-language results would otherwise crowd it out.
- Within viable preferred-language HTTP samples, choose resolution before excess measured speed. Low-throughput HD still loses to a source with headroom.
- Start immediately when preferred-language 1080p is already verified; otherwise allow only the existing 700 ms grace for a verified higher-quality/language choice.
- When torrent preloads fail, use seed availability hints for the normal-player fallback rather than blindly choosing the top quality result. Seed counts do not prove bandwidth or live peer availability.
- Retain the 4 GB and no-4K UI policy and existing real-player failover. Unknown size/resolution remain subject to available metadata checks; no startup guarantee is implied.

No upstream code was copied; these are original changes to MoonCat's GPL-3.0 code. Upstream project licensing remains relevant to any future copying.

## Validation

Regression coverage includes addon enablement/transport policy, probe-slot diversity, duplicate requests, fallback audio inclusion, YouTube handoff, dead high-quality fallback, verified HD selection, and immediate preferred-HD startup. The GitHub workflow must pass the unit suite, vital lint, release build, certificate verification, and packaged catalogue checks before publishing. Physical Fire TV playback is still required to measure actual startup and sustained performance.

# MoonCat audit — 9 November 2026

## Baseline and scope

Main was `e889988` (Beta 10). The newer published Beta 11 tag was `d7387f5`, containing bounded transient addon retries and a queue/timeout correction. Beta 12 includes that work.

The repository contains an Android TV Kotlin/Compose application derived from NuvioTV, bundled player/native engine libraries, local catalogues/branding, and an earlier experimental `mobile/` module. This audit follows the TV app's main execution paths and uses its full JVM regression suite; it is not a claim that every upstream codec/native binary or every screen was manually exercised.

## Architecture and feature inventory

| Area | Implementation and review |
| --- | --- |
| Frontend | Compose Home/catalogue rows, collections, search, details/seasons/episodes, source picker, player overlays, profiles, library, settings, news, parties and ambient mode. Existing layout/focus identity retained. Home already bounds catalogue loading and defers optional enrichment. |
| Discovery | `AddonPreferences`, `AddonRepositoryImpl`, `MooncatCatalogInterceptor`, `StreamRepositoryImpl`, stream DTO/mapper and Stremio resource/episode policies. Enabled per-profile addon configuration remains authoritative. |
| Playback | Metadata autoplay selector; direct HTTP/HLS, local torrent engine, YouTube, configured debrid resolvers and personal media-server routes. Exact URL/header/hash/file identities keep different routes distinct. |
| Recovery | Controller startup/torrent/observer/source-recovery paths, `SourceFailoverSession`, audio-language policy, episode identity, resume and next-episode preferences. |
| Memory and network | Existing memory-aware load control, parallel HTTP range path, torrent disk-cache cap, image-loader limits, shared source-search cache and cancellation boundaries. |
| Integrations | Optional watch-progress/tracking/account/library services are not required for source lookup. No external database schema or user-account settings are changed by this release. |
| Delivery | Gradle identity/version, GitHub Actions tests/signing, release selection, downloader and APK identity verification. README/build instructions were stale and are corrected for v2. |

The playback backend runs on the TV: coroutine-based repositories query independent upstream services and the native torrent engine exposes a local HTTP route. There is no dedicated MoonCat streaming server to provision or scale for this release. Existing personal-server and account integrations remain optional.

## Findings and changes

| Finding | User impact | Beta 12 change |
| --- | --- | --- |
| First provider response wins immediately | A slightly slower preferred-language or better-quality result is ignored | Bounded 250 ms comparison window, up to 750 ms total when preferred audio is missing; immediate return on completion |
| Size band before basic availability | A 3.2 GB torrent reporting zero seeds outranks a healthy smaller release | Direct/resolver and reported availability tiers before size; existing language/resolution and 3–3.5 GB preference retained |
| Recovery used generic ranking with a 100 GB default | Failover could choose a file above the household's 4 GB limit | Use the same capped metadata policy as initial playback |
| Binge match sorted again after selection | Next episode can lose its chosen release group | Preserve matching binge group at the best available language tier |
| Torrent initial startup always loads saved progress | “Start from beginning” resumes the old position | Respect the navigation flag for torrent startup |
| Stalled buffering could end the watchdog without recovering | Loader can remain before READY or when no buffer is ahead | Try bounded source recovery at the current position after 15 seconds without buffer advancement; exclude paused/live playback and restrict to movies/series/shows |
| Timeout measured using wall-clock time | Clock adjustment can misfire the watchdog | Measure elapsed time with the monotonic Android clock |
| Empty provider groups counted as cached success | Temporarily empty/filtered searches suppress a fresh retry | Require an actual stream before retaining a completed result |
| Concurrency budget was per search | Detail and player searches could multiply requests on a low-memory TV | Share four addon slots and two enrichment slots in the repository |
| Comparator repeatedly parsed labels and sizes | Wasted CPU while ordering hundreds of streams | Precompute each candidate's ranking fields |
| Count-only catalogue cache cleared every row at once | Memory not bounded by response size; unnecessary refetches | 4 MiB string-payload/48-entry LRU cache with expiry; oversized entries are not retained |
| Update HTTP work outlived cancellation; frequent UI callbacks | Duplicate or obsolete download work and unnecessary recompositions | Cancellable async call, 100 ms progress throttle, size verification/partial-file cleanup, download ownership and cancellation propagation |

## Existing sources and external checks

Default playback providers are Torrentio (per-profile Hindi/English configuration) and ThePirateBay+. The app also supports installed Stremio-compatible addons over HTTPS, official YouTube channel metadata, direct stream URLs and personal media servers. TorrentsDB remains explicitly excluded by the existing household policy. Bootstrap descriptors for other providers do not mean those providers are installed or enabled.

Manifest-only checks from the build environment on 9 November returned HTTP 403 for Torrentio, 503 for ThePirateBay+, and 200 for `v3-channels.strem.io`. These observations do not establish availability on the user's Indian home network. The YouTube descriptor advertises catalog/meta resources rather than a general movie stream source. No speculative providers were silently added; they would not establish reliable playable coverage and can slow discovery. Existing transient retry and independent parallel discovery remain in place.

## Research and decisions

- [Android TV memory guidance](https://developer.android.com/training/tv/playback/memory): constrained TV devices need bounded media/image allocations and must release resources between episodes. Applied bounded catalogue payload memory and shared request budgets; did not increase video buffers to mask network stalls.
- [Media3 customization](https://developer.android.com/media/media3/exoplayer/customization): load control, data sources and track selection are separate components. Retained the existing memory-aware player stack and changed source policy/recovery at their own boundaries.
- [Stremio stream specification](https://stremio.github.io/stremio-addon-sdk/api/responses/stream.html): file index, trackers, request headers, language/filename hints and binge groups are distinct parts of stream routing. Preserved transport/request identity and binge-group behavior. Subtitle labels do not prove audio availability.

The vendored Media3/native engine stack is coupled to local AARs. A blind dependency upgrade could break ABI/API compatibility and is deferred to a separately tested engine migration. No claim is made that an advertised seed count or a larger file guarantees throughput.

## Validation

The release workflow runs packaged-data validation, Python release-script tests, the full TV JVM suite, release assembly/vital lint, APK-signature/package/version checks and packaged-catalogue validation before publishing. Added regressions cover metadata timing/cancellation, availability ranking, size fallback/cap, binge groups, empty-result retry, catalogue eviction/expiry and update download completion/cancellation/truncation.

Physical TV verification still required: Play-to-first-frame on the same movie and series/network, manual source switch, resume versus restart, episode change, D-pad navigation, language/audio selection, 20-minute streaming and memory behavior. No connected Fire TV was available during this audit, so these outcomes and speed improvements are not claimed as measured.

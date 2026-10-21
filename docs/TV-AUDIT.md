# MoonCat TV audit — 21 October 2026

## Scope

Repository-wide inventory, source/build configuration review and Android compilation/unit validation, with detailed review of source discovery, episode identity, ranking/readiness, source screen state, image loading, studio catalogues, News connection lifecycle, signing and release automation. This is not a claim of line-by-line certification or that all defects are eliminated.

## Changes

| Finding | Change |
| --- | --- |
| `main` had older imported source while the newest APK source lived on another branch | Use the 1.0.5 source as the baseline; preserve its provider, audio and player changes. Consolidate the audited tree after validation. |
| Every 480p/360p source was discarded | Retain SD fallbacks after HD; keep resolution/size/CAM safeguards. |
| Bare IMDb series IDs could be queried without an episode suffix | Normalize standard episode IDs and preserve addon-specific IDs. Live TV resource types are not converted into series. |
| Saved-source keys could collide across episodes using bare series IDs | Use the episode-specific identity before constructing playback/cache keys. |
| Results stayed hidden while readiness probes ran | Publish source choices first, then resolve autoplay. Manual selection cancels the pending search. |
| A later provider response bypassed the first-page size limit | Preserve pagination across partial responses and filter changes. |
| Provider work had no local per-addon deadline | Limit addon concurrency to six; stream requests to 12 seconds and supported inline-meta fallback to four seconds. |
| Repeated source-policy regex construction and main-thread ranking | Compile reusable expressions once and parse/rank source lists on `Dispatchers.Default`. |
| Aggressive image concurrency and per-poster crossfade work | Eight image requests, four per host, two decodes; disable per-poster crossfades and remember studio previews. |
| Up to twelve large search sessions retained for fifteen minutes | Limit to four sessions and five-minute completed-result TTL. |
| Rapid News selections could launch an earlier channel later | Cancel the previous job/HTTP call, inspect a bounded playlist prefix and close responses. |
| Failed provider chips disappeared | Keep unavailable sources visible until refresh for diagnosis. |
| More sources requested | Add Comet and official YouTube connectors, bootstrap descriptors and one-time profile migration. Playback is not guaranteed; see SOURCES.md. |
| Updater could select another package or an incompatible signing key | Filter Preview assets separately and verify downloaded package, version and certificate against the installed app before installation. |
| Repository branding and docs lagged app assets | Use the current violet logo and rewrite README/build/signing guidance. |
| Multiple workflows imported old source or reassembled split binaries | One source-build workflow; old examples archived as text. Remove APK chunks, stale overlays and editor metadata from the current tree. Git history retains prior versions. |

## Validation

- Studio asset validator passed: 11 catalogues, 11,655 entries, branding resources present.
- Release-script suite passed: 21 tests.
- Android full unit suite, focused regression checks, release assembly, vital lint, signing and packaged-asset validation: consult the linked GitHub workflow for the exact candidate commit. Results will be recorded after completion.

## Limits and remaining verification

Physical Fire TV remote navigation, sustained streaming and frame-time measurements are not available in this environment. No percentage speedup is claimed. Missing provider content, regional restrictions, paid/DRM services and absent dubbed tracks cannot be repaired by adding catalogue names. Comet returned a valid manifest but sampled streams returned HTTP 403 here; YouTube returned 97 TV-category channel entries.

The inherited player also contains compatibility code for unvalidated TLS on media/subtitle connections. This audit does not treat those paths as a verified security boundary; a separate compatibility/security change is required before describing the whole application as hardened.

# MoonCat 1.1.0 — playback engine rebuild

- Progressive source discovery with independent provider failures, bounded concurrency and lifecycle cancellation. Discovery retains original URLs, headers, torrent file indexes and language evidence.
- New playback request session observes arriving results while waiting briefly for a preferred dub. HD starts in the player without downloading sample bytes from eight competing files. UHD remains explicitly qualified.
- Audio language requests run in the playback controller, select a supported embedded track first, and confirm actual selection. If needed they check bounded alternative files, preserve the playback position, and restore the original source when no verified dub is found.
- Consistent ISO language aliases, track-label fallback, codec-aware language choices and separate choices for unlabelled tracks. A filename or country flag never proves an embedded dub.
- Alternative-source recovery distinguishes CDN URLs and request headers, and excludes a failed local torrent's actual hash/file.
- Startup diagnostics no longer block the main thread. Poster revalidation uses a shared bounded scheduler.
- Original anime MoonCat branding across launcher icons, wordmarks, TV banners, splash and profile artwork. Screen layouts and theme controls are retained.

Provider availability, subtitle/audio metadata and dubs depend on the provider and media file. This release does not promise that every title has a working source or dub. See `docs/PLAYBACK-ENGINE-v1.1.0.md` for architecture and validation status.

The APK uses the separate MoonCat Preview package when the original production signing identity is unavailable. An older Preview with a different certificate must be removed first; the original MoonCat can remain installed.

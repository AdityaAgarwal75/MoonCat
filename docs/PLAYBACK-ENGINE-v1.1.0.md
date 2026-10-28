# Playback engine 1.1.0

## Decisions

The TV UI remains the presentation boundary. Existing protocol adapters and decoders remain; request orchestration, language policy, cancellation, source identities and recovery are rebuilt around explicit playback requests. Replacing functional Media3/MPV decoders with a custom decoder would add compatibility risk without fixing source selection.

Discovery emits raw provider groups progressively. Optional debrid availability annotation runs independently and cannot remove local-torrent fallbacks. Four addon requests and four plugin searches may run concurrently. Addon/server/plugin work has independent deadlines and cancellation. A shared cache retains active requests, grants a two-second navigation handoff, and stops abandoned work. Completed results expire after sixty seconds; refresh bypasses them.

PlaybackSourceSession observes fresh candidate snapshots while the collector keeps receiving providers. Preferred-language direct candidates can start immediately. Other candidates receive a maximum 1.2-second language grace; completed searches finish the grace immediately. Native preparation is bounded. HD readiness is established by actual playback and bounded recovery, rather than competing sample downloads. Only opted-in UHD uses short qualification probes. Autoplay's conservative quality/file-size rules do not hide manual discovery results.

Explicit language requests differ from passive preferences. AudioLanguagePolicy normalizes ISO language tags and recognizes unambiguous embedded labels. The controller first selects supported tracks in the current container and waits for the actual selected-track event. Otherwise it tries at most three distinct sources within a sixty-five-second request budget. Source labels rank search hints; they never establish success. The original playback position is captured once, preserved across source checks and restored if the requested audio cannot be verified. Manual selection, episode changes and lifecycle disposal cancel pending language work. Unknown embedded tracks remain individually selectable.

Request identities include the actual transport, URL, headers and torrent file index. Identity digests do not expose authorization or signed URLs. Failed local-torrent localhost URLs also mark their original hash/file failed. A failed CDN does not suppress another CDN carrying the same torrent hash.

Startup diagnostics initialize asynchronously. Image revalidation shares one client and scheduler with two active requests, sixteen pending requests and bounded cooldown history. These changes address observed code paths; they are not a measured Fire TV frame-time claim.

## Sources and extensibility

Configured Stremio manifests, existing JS/DEX adapters, personal media servers and YouTube remain the supported expansion paths. Adding duplicate public indexes by default would increase requests without proving more playable dubs. Endpoint observations and their limits are recorded in [SOURCES.md](SOURCES.md). No paid account or fabricated provider endpoint is introduced.

Research: [Media3 track selection](https://developer.android.com/media/media3/exoplayer/track-selection), [Android threading](https://developer.android.com/topic/performance/threads), [Stremio stream protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/stream.md), [startup optimization](https://developer.android.com/topic/performance/appstartup/analysis-optimization). ECC Android architecture, coroutine, regression and review skills guided the separation of policies from controller/presentation work.

## Branding

Original mascot, wordmark and TV banner were generated with the built-in image generation tool and exported to the existing Android resource sizes. Resource identifiers retain their historic suffix to avoid layout changes. Source artwork is under `branding/anime/`; packaging is reproducible with `scripts/package_anime_branding.py` (Pillow required).

Prompts: premium cream anime kitten with violet eyes curled on a lilac crescent, transparent mascot; matching transparent MoonCat wordmark; matching midnight-violet celestial 16:9 MoonCat TV banner. No third-party character identity was requested.

## Validation

Passed on 27 October 2026: the full Android TV unit suite, all 24 release-script tests, asset validation, optimized release assembly, vital lint, APK signature verification and packaged catalogue checks. [GitHub Actions evidence](https://github.com/AdityaAgarwal75/MoonCat/actions/runs/40456330691). Sixteen focused language/menu/identity tests also passed locally. The local full build exceeded available host memory; the successful final build ran on GitHub.

Published APK: [MoonCat 1.1.0 Preview Fire TV](https://github.com/AdityaAgarwal75/MoonCat/releases/download/v1.1.0-rc.1/MoonCat-v1.1.0-Preview-FireTV.apk), 59,481,188 bytes. Package `dev.mooncat.tv.preview`, version `1.1.0` / `10009`, source revision `40da546155e94bfba82fa990a8750c12bdf5fe5c`. SHA-256: `ab5661fdcf980828f2c1295f11738414ac28744fe799146919218a7273905e71`.

Physical Fire TV remote navigation, sustained playback and real-device dub switching remain unverified in this environment. The original production signing identity remains unavailable; this is a separately signed Preview app.

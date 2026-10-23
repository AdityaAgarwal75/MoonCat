# MoonCat 1.0.7 validation and scope — 23 October 2026

App source revision: `417ce377cc936a3c502c1380849680fa27fe39da`. Subsequent commits fix the test harness, validate recorded evidence and update documentation; app code/assets are unchanged.

## Concrete repairs

| Problem | Resulting behavior |
| --- | --- |
| Missing Hindi audio triggered automatic file changes after playback began | Removed automatic language-source repair. Track preference applies inside the current file; source search requires an explicit user action. |
| First empty/unsuitable provider result disabled autoplay before slower results arrived | Keep selection active while the provider search continues; conclude only on a resolved source, completed search, manual choice or watchdog. |
| Only preferred-language HD responses could start early; a ready English HTTP source waited for a torrent | Start on playable direct results and accept a ready HTTP winner without waiting for torrent preparation or another dub. |
| A failed short readiness sample could prevent all automatic playback | Try a ranked HD candidate in the real player; reject unverified 4K instead of using it as this fallback. |
| Dead links forced the user back to source selection | Bounded per-title/episode recovery tries distinct alternatives on actual player failure. Missing/forbidden HTTP files bypass decoder retries. Explicit source selection cancels pending recovery/autoplay. |
| Debrid “not cached” status hid torrents the local engine could stream | Prefer already-ready cached candidates while retaining uncached local-engine fallbacks. |
| Torrent source state could lose its file index | Carry effective hash/file-index identities across HTTP/torrent and episode source changes. |
| Explicit retry reused a stale saved link and earlier failure history | Refresh/retry clears that attempt history and bypasses saved-link reuse. |
| Optional TMDB work blocked the title and Play button | Render available metadata first. Enrichment is bounded to 4 seconds; the originating metadata request has a 2.5-second head start, individual metadata providers 4 seconds, and remote-progress wait 2 seconds. Catalogue requests have an 8-second deadline. |
| Catalogue mapping ran on the UI dispatcher | Move catalogue retrieval/mapping to IO; retain existing background stream ranking. |
| Release optimization was disabled; large background resources could inflate with device density | Enable R8 with app/reflection/JNI compatibility rules. Export static brand backgrounds as `drawable-nodpi`. |
| Regional Home shelves were unwanted | Remove Tamil/Telugu and other named regional shelves, including persisted addon manifests. Do not filter media files, audio tracks, News or studio collections by those Home preferences. |
| Brand assets were inconsistent | New cream sleeping kitten/lilac crescent identity across all themed launcher icons, wordmarks, TV banners, splash, profile background and repository logo. |

## 4K behavior

The optional Playback setting defaults off. It requires hardware 4K decode capability and a direct source yielding a full 2 MiB sample at 8 MB/s or more (64 Mb/s). Slow, failed or undersized samples cannot select unchecked 4K. Remux, 8K and torrent-only 4K remain excluded, and existing conservative file-size safeguards remain. Adaptive manifests without a qualified 4K identity remain constrained to HD. A short probe is not a promise of sustained server speed or codec compatibility; actual failures use HD source recovery.

## Validation

- Passed: [TV unit suite, release assembly, vital lint, signing and packaged-asset checks](https://github.com/AdityaAgarwal75/MoonCat/actions/runs/39976999711).
- 24 release-script tests passed, including package-scoped Java/native crash, ANR and process-liveness checks.
- Asset checks verified all 11 studio collections and 11,655 entries, plus the five brand resources.
- Optimized x86 Android TV APK built and ran on API 28. Captured Home → title Play page → Home remote navigation, UI XML, screenshots, launch output and final live-process memory report.
- The [original emulator run](https://github.com/AdityaAgarwal75/MoonCat/actions/runs/39976999771) failed an overbroad all-process crash assertion because the emulator's **com.android.tv** DVR process crashed on its own database schema. MoonCat remained alive and its pages rendered. The captured evidence was inspected and [independently revalidated in GitHub](https://github.com/AdityaAgarwal75/MoonCat/actions/runs/39980393072) after scoping crash checks to MoonCat. The original log/artifact is retained; this is not a claim that its workflow was green.

## Limits

Physical Fire TV frame times, sustained movie playback, every provider/title/dub and real-device 4K decoding are not certified here. No percentage speedup or “every bug fixed” claim is made. Community provider content and server availability remain external dependencies. The original production signing key is unavailable, so the generated APK is MoonCat Preview, with separate settings; an older Preview using a different CI key can require removal before installation.

Engineering references: [Android Compose performance](https://developer.android.com/develop/ui/compose/performance), [Media3 audio track preferences](https://developer.android.com/reference/androidx/media3/common/TrackSelectionParameters.Builder), and [Android Emulator Runner configuration](https://github.com/ReactiveCircus/android-emulator-runner).

## Published APK

[MoonCat-v1.0.7-Preview-FireTV.apk](https://github.com/AdityaAgarwal75/MoonCat/releases/download/v1.0.7-rc.1/MoonCat-v1.0.7-Preview-FireTV.apk) — 58,253,652 bytes; `dev.mooncat.tv.preview`, version `1.0.7` / `10008`, `armeabi-v7a`, Android 7+.

SHA-256: `6dbdd5235c6a5ce781b5d5ebab8f0848b535d53c2b90bbcf4be4cf27290b46ee`.

# V3 validation ledger

- 86 focused JVM tests passed on 13 November 2026 using real Stream, audio-language, source policy, preferences, autoplay selector, session, request identity, failure history, recovery gate and provider-budget implementations. A standalone Kotlin compiler runner supplied minimal Android annotation, build-feature, server-target and enum boundary fixtures; this does not replace the full Android suite.
- Regression-first evidence: source availability tests failed before ranking changes; preference/gate/budget APIs failed before implementation; the final debrid-badge preference regression failed and then passed after preserving the household ranking.
- 24 release-tool Python tests passed. All 11 studio catalogues /11,655 entries and five branding assets validated. git diff --check passed.
- An independent reviewer checked changed playback paths. Corrections included initializer ownership, replacement initializer handoffs, anime IDs, next-episode language shortcuts, search deadlines and failure-history synchronization.
- Full Android test/assembly verification is delegated to the GitHub Actions run on redesign/mooncat-v3. The local full build was stopped to prioritize the owner-requested GitHub build; no local full-suite success is claimed.
- No Android/FireTV device is attached (adb devices returned an empty list). Real click-to-first-frame latency, sustained buffering, decoder/audio support and remote navigation remain device checks. Per-stage deadlines are not an end-to-end latency guarantee.

# MoonCat 3.0.0-alpha.3 — broader source availability and fast failover

- Manual discovery shows every returned source except known 4K-or-higher streams and files with known size exceeding 6,000,000,000 bytes. CAM, telesync, screener, PreDVD, SD, 720p, 1440p, unknown-size, and other releases are no longer removed. External watch links can appear manually but are not autoplayed as files.
- 4K stays blocked even if a previously stored opt-in setting existed; the now-misleading 4K toggle was removed.
- All enabled compatible HTTPS providers, including configured TorrentsDB instances, are eligible for discovery; actual provider health and availability are independent.
- Autoplay and failure recovery consider every eligible non-4K source under the 6 GB cap. Highest advertised peer/seeder counts guide ranking among otherwise suitable files; clean releases are preferred to CAM when both exist, while CAM-only titles remain playable.
- Hindi/original-language preferences influence ranking rather than suppressing all alternatives. Playback retains first-stream fast start, native engine preparation, source failure recovery and Fire TV memory protections.
- Known-size and resolution metadata can be wrong or absent; actual decoder compatibility and sustained throughput are not guaranteed by labels or peer counts. Start in 1–2 seconds is a target, not a guarantee for torrents.
- Version code 30002, package `dev.mooncat.tv.v2` and trusted V2 signature remain unchanged when release signing is available. If signing secrets are absent, CI produces an isolated Preview APK rather than a misleading in-place update.
- Regression tests cover CAM-only autoplay, file size and resolution boundaries, unknown-size files, 4K opt-in overrides, provider gating and availability ranking. Physical Fire TV startup and buffering still require device testing.

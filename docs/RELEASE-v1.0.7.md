# MoonCat 1.0.7 — playback and responsiveness

- A missing Hindi track no longer searches other files or interrupts a selected/playing source. Hindi remains a track/source preference; English and original-language files remain playable.
- Begin automatic selection when a playable direct source arrives instead of waiting for a Hindi 1080p label or every provider. A ready HTTP source no longer waits for torrent preparation.
- Short readiness probes are hints, not a hard playback gate. If all probes fail, try the ranked source in the actual player.
- On actual playback failure, try up to four distinct alternative sources for that title/episode. Do not repeat failed URLs or torrent files. Manual source selection cancels recovery. The error remains visible when the bounded alternatives are exhausted.
- Show title details and Play before optional TMDB enrichment. Bound enrichment/remote-progress waits and keep the selected season while enrichment arrives.
- Move catalogue parsing/mapping to a background dispatcher. Enable R8 release optimization while retaining app/JNI/plugin reflection contracts.
- Parent source-discovery scopes to their screen/player lifecycle; avoid leaked discovery work. Preserve available results when a different provider reports an error.

Validation is recorded in the associated GitHub Actions run: TV unit suite, release-script tests, asset checks, optimized release assembly, vital lint, APK identity/signature checks. These checks do not measure frame times or certify physical Fire TV playback. Providers can return unavailable links or content without a dub; the app cannot manufacture either.

When original signing secrets are unavailable this release is a separate MoonCat Preview app. An older Preview signed by a different CI key may need removal before installation; the original MoonCat app can remain installed. Preview settings are separate.

Language and quality:
- Remove Tamil/Telugu and other regional shelves from Home discovery only. Preserve every playable source and audio language; Hindi/English are preferences rather than file exclusions.
- Optional “4K on fast servers” setting defaults off. Requires hardware 4K decoder support and a direct HTTP source that supplies a full 2 MiB sample at at least 8 MB/s (64 Mb/s). Failed/slow/undersized samples never fall back to unchecked 4K. Torrents, remux and 8K remain excluded. This is a short readiness measurement, not a sustained throughput guarantee.

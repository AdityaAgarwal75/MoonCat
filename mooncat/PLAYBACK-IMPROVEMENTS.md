# Playback and discovery changes after v1.0.4

## Free providers

Existing Torrentio and MediaFusion installations are preserved. TorrentsDB (`https://torrentsdb.com`) and HDHub (`https://hdhub.thevolecitor.qzz.io`) are added once to the Family profile. Provider removal, disabled states and custom URLs are preserved after that migration. Both additions are stream-only, so their catalogues do not duplicate Home.

Live metadata checks on 2026-10-20 for `tt0241527` returned 82 TorrentsDB results, including two Hindi/English-labelled releases, and 25 HTTP URLs from HDHub. Three size-eligible 1080p HTTP links returned HTTP 206 and 65,536 bytes of Matroska media each. A header-only ffprobe check confirmed one file is 1920×1080 HEVC with Hindi E-AC-3 and English AAC audio tracks. No full film was downloaded for this check. These counts include releases that MoonCat intentionally filters out, including 4K and large files. Source labels and an available URL do not establish that a dub is present or that sustained playback will work. Public providers can fail, expire links or restrict an IP/region.

Research: Reddit's [free HTTP alternatives discussion](https://www.reddit.com/r/StremioAddons/comments/1sy3giu/what_are_the_best_free_alternatives_for/) recommends HDHub and other HTTP addons. AIOStreams' maintained [HDHub integration](https://github.com/Viren070/AIOStreams/blob/main/packages/core/src/presets/hdhub.ts) documents free CDN operation without TorBox. Its [TorrentsDB integration](https://github.com/Viren070/AIOStreams/blob/main/packages/core/src/presets/torrentsDb.ts) identifies the independent torrent provider. These integrations were read as protocol references; their source code was not copied.

Nuvio Streams was investigated and rejected because AIOStreams marks its public instance deprecated. WebStreamr's public instances, Comet and Sootio returned HTTP 403 in this cloud environment. Peerflix responded but its sampled Harry Potter results did not advertise Hindi and largely exceeded the device's quality/size limits, so it was not added as a default. No paid service or personal credential is required by the new defaults.

## Selection and playback

- Shared source presentation filters reject camera recordings, 4K/8K, resolutions above 1080p, remuxes and oversized releases. Movie/episode limits remain 7 GB/2 GB. Structured resolution fields and raw filenames are checked alongside provider text.
- Hindi is the preferred language, English the fallback, followed by unlabelled audio. Subtitle labels do not establish a Hindi dub. Existing explicit language settings remain respected.
- Up to eight distinct candidates are considered, reserving fallback slots when many Hindi labels are available. HTTP and torrent checks run together rather than serially.
- HTTP probes read at most 64 KiB per resource. HLS checks follow a supported variant and sample a media segment, rather than treating playlist transfer speed as media throughput. Each candidate has a bounded timeout. Healthy 1080p sources outrank merely faster 720p results; English remains available when Hindi checks fail.
- Torrent candidates share the existing engine and preload 64 KiB each. Selection gives better language/quality candidates a bounded opportunity to become ready rather than taking the first file unconditionally. Losing torrent routes are stopped; failed/unverified candidates are not silently selected as a fallback.
- Binge-group shortcuts no longer bypass the initial provider wait and readiness checks. The provider wait uses the configured timeout up to eight seconds instead of silently capping it at 2.5 seconds.
- A preferred-language 1080p HTTP candidate can start its readiness checks before every provider responds. If it passes those checks, playback can start without waiting for the parallel torrent search.
- When autoplay starts a file without Hindi tracks, one bounded background source search can verify other Hindi-labelled files against their decoded tracks. It restores the original file if candidate audio claims are wrong and never switches automatically without a restorable original source.
- Adaptive ExoPlayer video selection is limited to 1920×1080. Native, custom and stock buffering target more forward content, with a longer restart buffer after a stall. Device memory budgets remain enforced. Increasing the buffer can absorb temporary speed dips; it cannot sustain a source permanently slower than the video bitrate.

## TV interface and catalogues

Studio pages use the shared theme, typography, poster cards and focus styling. Studio browsing uses a lazy grid and shared row focus restoration. Filters show their selected state through the theme rather than prefixing a bullet. One missing packaged collection does not prevent every other collection from loading.

Series details expose a latest released episode action beside Play. Unknown/future release dates, unavailable episodes and season-zero specials are excluded; Resume is unchanged.

Home gains Hindi dubbed movies, Indian OTT releases, a small verified Hindi TV serial discovery shelf, Tamil/Telugu/Malayalam/Punjabi films, family movies, comedy/crime series and documentaries. Existing language, worldwide releases, upcoming, airing and anime rows remain. Discovery is not proof of source availability. Regional-provider metadata can be incomplete or misclassified; normal details/source lookup remains authoritative.

The Hindi serial shelf uses TVmaze's public metadata with stable IMDb IDs and posters. Metadata attribution: [TVmaze](https://www.tvmaze.com/api), [CC BY-SA](https://creativecommons.org/licenses/by-sa/4.0/). Names and IMDb IDs were selected from its API on 2026-10-20; the shelf is curated rather than a complete list of Hindi television.

## Validation and test build

The final development build on 2026-10-20 passed 71 focused JVM tests with no failures or skips, `assembleFullDebug` including its required lint-vital check, APK signature verification, and the packaged catalogue contract (11 studios, 11,655 entries). Full lint and the full test suite were not completed. A software emulator did not finish booting, so TV focus/navigation and sustained playback still require device validation.

The supplied v1.0.5 APK targets armeabi-v7a and uses the separate `dev.mooncat.tv.debug` package with a development signature. It can be installed alongside the published app; it does not update that installation or inherit its settings. No GitHub release was published.

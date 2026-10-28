# Source integration research — 21 October 2026

The app implements the [Stremio addon protocol](https://stremio.github.io/stremio-addon-sdk/protocol.html). Episode IDs must refer to a video, not merely its parent series. [Stream objects](https://stremio.github.io/stremio-addon-sdk/api/responses/stream.html) distinguish direct URLs, torrents, YouTube IDs and external webpages.

| Integration | Decision and observed result |
| --- | --- |
| [Official YouTube addon](https://github.com/Stremio/stremio-official-addons) | Added to defaults and a one-time migration. Manifest reachable; TV-category catalogue returned 97 channel entries. Channel metadata carries embedded video streams; this is not a universal IMDb episode resolver. |
| [Comet](https://github.com/g0ldyy/comet) | Added an independent stream connector at `https://comet.elfhosted.com`. Manifest reachable, stream resource supports movies/series with IMDb/Kitsu IDs. Sample stream requests returned HTTP 403 in the build environment, so playback coverage is unverified. |
| Existing Torrentio, MediaFusion, TorrentsDB, HDHub | Retained current configuration. More connectors do not establish more unique or playable sources. |
| [AIOStreams](https://github.com/Viren070/AIOStreams) | Researched. Requires an actual configured instance/manifest. Not installed with a fabricated URL or account credentials. Existing Addons accepts configured Stremio manifests. |
| [Magnetio](https://github.com/peterdsp/Magnetio) | Researched. Public manifest returned 403 here; not automatically installed. Self-hosting would be a separate service deployment. |
| Official WatchHub / Public Domain Movies | Listed by Stremio, but tested endpoints returned 403 here. Not automatically installed as working sources. WatchHub uses external provider links rather than free embedded episodes. |
| Foreign Public Domain addon | Tested public hosts returned 503/404. Not automatically installed. |

Tests in this environment cannot determine availability on the user's Indian ISP or guarantee coverage for daily serials. No DRM, account login, subscription requirement, or access control is bypassed.

## Discovery redesign — 26 October 2026

The new discovery boundary retains provider candidates intact. Device autoplay
limits, preferred quality, language ranking and debrid display preferences must
not erase files from the shared discovery cache: a file rejected for automatic
startup may still contain the explicitly requested dub. Optional debrid cache
annotations now run independently after the first result is published, with two
concurrent checks and a 2.5-second deadline. They cannot block another provider.

The backend respects the [manifest resource/type/ID-prefix contract](https://stremio.github.io/stremio-addon-sdk/api/responses/manifest.html).
[Stream request headers, torrent file indexes and direct versus torrent transports](https://stremio.github.io/stremio-addon-sdk/api/responses/stream.html)
are part of candidate identity. Combining records solely by torrent hash can
replace a ready direct URL with an uncached torrent or collapse episode files;
combining solely by URL can discard a required Referer or authentication header.
Different dub labels remain evidence until the player inspects actual tracks.

[Android coroutine guidance](https://developer.android.com/kotlin/coroutines/coroutines-best-practices)
supports caller-controlled lifetimes and propagating cancellation. Searches now
share work while observed, retain a two-second navigation grace, and cancel
abandoned work. Completed results expire after one minute; explicit refresh
always starts a new session. JS/DEX execution uses four background workers
instead of ten. JavaScript providers start independently of DEX preparation;
one failed DEX setup does not fail JavaScript results. Plugin single-flight
registration is atomic. These are architectural limits, not measured Fire TV
frame-time claims.

### Endpoint observations

Public manifest GETs from the build executor on 26 October 2026:

| Existing integration | Observed HTTP result | Scope |
| --- | --- | --- |
| Comet / ElfHosted | 200, version 2.0.0 | Stream resource for movie/series; IMDb and Kitsu prefixes. |
| MediaFusion / ElfHosted | 200, version 6.2.0 | Stream resources for movie/series/TV/events with declared ID prefixes. |
| HDHub | 200, version 1.0.7 | Stream/catalog resources, movie/series and provider-specific type. |
| Official YouTube | 200, version 1.30.7 | Catalog/meta channel integration; not an IMDb movie source. |
| Torrentio configured `language=hindi` manifest | 403 in executor | Base manifest was visible to web retrieval. This does not establish configured-request access on a Fire TV. |
| TorrentsDB | 403 in executor | A web-retrieved manifest exists; executor access differs. |

These checks establish manifest availability only, not successful movie playback,
source coverage, Hindi dubbing, or availability on the user's ISP. No signed
media URLs or credentials are retained. No new public host is auto-installed
merely to increase provider count. Existing Addons accepts an actual configured
Stremio manifest (including a self-hosted AIOStreams/Comet/MediaFusion instance),
while existing JS/DEX repositories remain separate source adapters. Configuration
and account requirements belong to the actual provider; invented fallback URLs
cannot make those services work.

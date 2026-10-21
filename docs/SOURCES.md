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

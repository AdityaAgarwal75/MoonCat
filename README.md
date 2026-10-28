<p align="center"><img src="branding/mooncat-logo.png" width="160" alt="MoonCat violet moon and cat logo"></p>

# MoonCat TV

A remote-first media discovery and playback app for Android TV and Amazon Fire TV, derived from [NuvioTV](https://github.com/NuvioMedia/NuvioTV).

[Download APKs](https://github.com/AdityaAgarwal75/MoonCat/releases) · [Build status](https://github.com/AdityaAgarwal75/MoonCat/actions/workflows/mooncat-tv.yml) · [Build guide](docs/MOONCAT-BUILD.md) · [Latest audit](docs/PLAYBACK-ENGINE-v1.1.0.md)

## What it does

- Browse movies, series and anime with Hindi/English Home discovery shelves.
- Explore 11 studio collections with 11,655 catalogue entries and poster grids.
- Search installed Stremio-compatible providers concurrently, with per-request deadlines.
- Prefer Hindi/English and HD, keep playable language/quality fallbacks, and try alternative sources after real playback failures.
- Use the official YouTube catalogue, configured addons, or personal media servers.
- Browse 20 Indian news channels, grouped by language.
- Optional **4K on fast servers** in Playback settings: hardware 4K support plus a larger speed sample; otherwise use HD.
- Resume viewing, switch audio/subtitles, and check GitHub for updates.
- A missing Hindi track never automatically replaces a playing file. Explicit language-source search stays available in the audio menu.

Sources are supplied by independent services. Catalogue entries do not guarantee a playable source, a specific dub, or access in every region. External watch links open separately from embedded video streams.

## Install the right APK

The TV target is Android 7.0+ and `armeabi-v7a`, including Fire TV Stick 3rd generation. A regular update must retain `dev.mooncat.tv` and the original signing certificate. **MoonCat Preview** uses `dev.mooncat.tv.preview` and installs alongside it when trusted signing is unavailable. Profiles and settings are separate in Preview.

The `mobile/` directory is an earlier experimental app, not a feature-complete phone version of this TV app.

## Build and validate

Use JDK 17 and Android SDK 36. The Gradle wrapper is pinned to 8.13.

```bash
python3 scripts/validate_tv_assets.py
./gradlew :app:testFullDebugUnitTest
./gradlew :app:assembleFullRelease
```

See the [build guide](docs/MOONCAT-BUILD.md) for signing, test fixtures and isolated preview builds. APKs and checksums are release assets; source control does not contain split APK uploads. One active workflow builds and validates the checked-in source. Historical workflow examples live in `docs/archive/workflows/` and cannot run.

## Attribution

Distributed under [GPL-3.0](LICENSE). See [NOTICE.md](NOTICE.md) for upstream attribution and [source research](docs/SOURCES.md) for provider references.

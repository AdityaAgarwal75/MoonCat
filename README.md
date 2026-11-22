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

## Android phones and Windows

The Alpha 4 companion clients share the TV catalogues, branding and a responsive interface. Android uses a touch layout and Media3 playback. Windows uses a resizable WebView2 window with keyboard navigation, Ctrl+K search, arrow-key seeking and an HLS player. These clients have local watchlists and progress. Movie and series playback accepts configured Stremio-compatible providers that return direct HTTPS URLs. Raw torrent hashes, TV account sync and TV-specific plugin settings are outside this release.

Build the APK with the same Android SDK and JDK listed below. A release APK requires `MOONCAT_CLIENT_KEYSTORE`, `MOONCAT_CLIENT_STORE_PASSWORD`, `MOONCAT_CLIENT_KEY_ALIAS`, and `MOONCAT_CLIENT_KEY_PASSWORD`. A debug APK uses the separate preview package and the local debug signer.

```bash
./gradlew -p mobile assembleDebug lintDebug
python3 desktop/prepare.py
cd desktop
go test ./...
go run github.com/akavel/rsrc@v0.10.2 -arch amd64 -ico mooncat.ico -o mooncat_windows_amd64.syso
GOOS=windows GOARCH=amd64 CGO_ENABLED=0 go build -trimpath -ldflags='-s -w -H windowsgui' -o MoonCat-Windows-x64.exe .
```

The Windows executable is portable and requires the Microsoft Edge WebView2 Evergreen Runtime.

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

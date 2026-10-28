<p align="center">
  <img src="branding/v2/mascot.webp" width="190" alt="MoonCat kitten on a crescent moon">
</p>
<h1 align="center">MoonCat TV</h1>
<p align="center">A cozy home for movies, series, and anime on your TV.</p>
<p align="center">
  <a href="https://github.com/AdityaAgarwal75/MoonCat/releases">Download MoonCat</a> ·
  <a href="https://github.com/AdityaAgarwal75/MoonCat/actions/workflows/mooncat-tv.yml">Build status</a> ·
  <a href="docs/MOONCAT-BUILD.md">Build guide</a>
</p>

<p align="center"><img src="branding/v2/scene.webp" width="800" alt="MoonCat in a cozy anime-style scene"></p>

## Made for the big screen

MoonCat is a remote-first Android TV and Fire TV app for discovering and playing movies, series, and anime. It is derived from [NuvioTV](https://github.com/NuvioMedia/NuvioTV).

- Browse curated collections and resume what you were watching.
- Find streams through Torrentio, with parallel readiness checks and manual source selection.
- Hide identified 4K sources and files reported above 4 GB; sources with missing metadata may still appear.
- Use the AMOLED theme, audio and subtitle controls, and GitHub update checks.

Sources come from independent services; availability, playback speed, and language options vary. A source search timeout does not guarantee that playback will start within the same time.

## Install or update

Download the Fire TV APK from [GitHub Releases](https://github.com/AdityaAgarwal75/MoonCat/releases). The v2 beta installs separately from pre-v2 MoonCat because the earlier signing key was unavailable; profiles and settings do not automatically move over. Later v2 releases use the same signing identity and can be installed as updates, subject to Android confirmation. Beta versions require the beta update channel.

## Build and verify

Use JDK 17 and Android SDK 36:

```bash
python3 scripts/validate_tv_assets.py
./gradlew :app:testFullDebugUnitTest
./gradlew :app:assembleFullRelease
```

See the [build guide](docs/MOONCAT-BUILD.md) and [release notes](docs/RELEASE-v2.md). The experimental `mobile/` directory is not the Fire TV app.

## Credits

Distributed under [GPL-3.0](LICENSE). See [NOTICE.md](NOTICE.md) for upstream attribution.

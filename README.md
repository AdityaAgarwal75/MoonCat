# MoonCat

MoonCat is an Android TV media discovery and playback app derived from Nuvio. It is being prepared for Amazon Fire TV Stick (3rd generation), with an `armeabi-v7a` build.

![MoonCat app icon](branding/mooncat-logo.png)

## Features

- Two local profiles.
- Hindi and English original-language discovery through the public TMDB addon.
- Cinemeta catalog pagination; Kitsu anime catalogs for airing, trending, popular, and upcoming titles; and TVMaze airing listings.
- Playback checks up to seven candidate sources concurrently, with each HTTP probe limited to 64 KiB and 1.5 seconds. It checks source availability rather than downloading complete torrents.
- Prefers 1080p and falls back to 720p. Search limits are 7 GB for movies and 2 GB for episodes.
- Embedded audio tracks are grouped for Hindi, English, and Japanese. If a requested language is missing, MoonCat searches a hinted source, verifies the actual tracks, and rolls back when possible.
- News opens first-party browser links. In-app news streams are not verified.

Catalog and source availability depend on third-party services and can vary by region. Some providers returned HTTP 403 in the current build environment, so provider coverage remains unverified.

## Build

The app uses Kotlin, Jetpack Compose, Android SDK 36, JDK 17, and the Gradle 8.13 wrapper. Build the full release variant with:

```bash
./gradlew :app:assembleFullRelease
```

Release signing is supplied through the `NUVIO_RELEASE_STORE_FILE`, `NUVIO_RELEASE_KEY_ALIAS`, `NUVIO_RELEASE_KEY_PASSWORD`, and `NUVIO_RELEASE_STORE_PASSWORD` environment variables. See [docs/MOONCAT-BUILD.md](docs/MOONCAT-BUILD.md) for release and verification details. The release APK has not yet been built or verified.

## License

MoonCat is distributed under the GNU General Public License v3.0. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for license and attribution information.

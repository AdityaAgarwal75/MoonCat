# MoonCat release build

## Target

- Variant: `fullRelease`
- Application ID: `dev.mooncat.tv`
- Version: `1.0.0` (version code `10001`)
- ABI: `armeabi-v7a`
- Intended device: Amazon Fire TV Stick (3rd generation)
- Toolchain: Android SDK 36, JDK 17, Gradle wrapper 8.13
- GitHub updater repository: [`AdityaAgarwal75/MoonCat`](https://github.com/AdityaAgarwal75/MoonCat)

## Build and test commands

```bash
./gradlew :app:assembleFullRelease
```

Targeted unit tests:

```bash
./gradlew :app:testFullDebugUnitTest \
  --tests 'com.nuvio.tv.core.mooncat.*' \
  --tests 'com.nuvio.tv.core.catalog.*' \
  --tests 'com.nuvio.tv.ui.screens.player.AudioLanguageChoicesTest' \
  --tests 'com.nuvio.tv.ui.screens.player.AudioSourceIdentityTest'
```

Build and test status is pending. Some provider endpoints returned HTTP 403 in the current environment; this does not establish their behavior for end users. Do not describe an APK as built or tested until the commands complete successfully.

## Signing

For a local release build, provide the keystore path and credentials through these environment variables:

```text
NUVIO_RELEASE_STORE_FILE
NUVIO_RELEASE_STORE_PASSWORD
NUVIO_RELEASE_KEY_ALIAS
NUVIO_RELEASE_KEY_PASSWORD
```

The repository's Gradle build reads these variables for release signing. Do not commit the keystore or credentials. The stable private signing key is maintained outside this repository. The planned GitHub Actions workflow will read `MOONCAT_KEYSTORE_BASE64`, `MOONCAT_KEYSTORE_PASSWORD`, and `MOONCAT_KEY_ALIAS` from repository secrets and map the decoded keystore and credentials into the Gradle signing inputs.

## Playback and discovery behavior

Source selection sends up to seven concurrent HTTP probes. Each probe is capped at 64 KiB and 1.5 seconds; it does not fetch seven full torrents. Search prefers 1080p, falls back to 720p, and applies size limits of 7 GB per movie and 2 GB per episode.

Embedded audio tracks are grouped for Hindi, English, and Japanese. When the selected file lacks a requested language, MoonCat searches a hinted source, checks the real audio tracks, and rolls back the source change when possible. News items are first-party browser links; in-app news streams have not been verified.

## Attribution

MoonCat derives from Nuvio development commit `c613e93111353283b42c12d516788c19df504c3c` and retains the upstream GPL-3.0 license and applicable notices. See [NOTICE.md](../NOTICE.md).

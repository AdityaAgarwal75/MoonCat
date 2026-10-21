# MoonCat TV build and signing

## Current target

- Version 1.0.6, versionCode 10007.
- Full TV variant: `:app:assembleFullRelease`.
- Android 7.0+, compile/target SDK 36, JDK 17, Gradle 8.13.
- ABI defaults to `armeabi-v7a`; `-PmooncatAbi=arm64-v8a` overrides it for development.
- Standard package: `dev.mooncat.tv`. Preview: `dev.mooncat.tv.preview`.

## Validation

```bash
python3 scripts/validate_tv_assets.py
./gradlew :app:testFullDebugUnitTest --no-daemon --max-workers=2
./gradlew :app:assembleFullRelease --no-daemon --max-workers=2
python3 scripts/validate_packaged_catalogues.py path/to/app.apk
```

The full debug unit suite needs non-secret test fixtures `TRAKT_CLIENT_ID=mooncat-ci-fixture` and `TRAKT_CLIENT_SECRET=mooncat-ci-fixture` in `local.properties`. Remove them before release packaging. The workflow performs this separation. Release assembly includes vital lint; no lint tasks are excluded.

## Signing

Trusted releases require the existing private key and these local environment variables:

```
NUVIO_RELEASE_STORE_FILE
NUVIO_RELEASE_STORE_PASSWORD
NUVIO_RELEASE_KEY_ALIAS
NUVIO_RELEASE_KEY_PASSWORD
```

GitHub Actions reads `MOONCAT_KEYSTORE_BASE64`, `MOONCAT_KEYSTORE_PASSWORD` and `MOONCAT_KEY_ALIAS` from repository secrets. It verifies the certificate SHA-256 is `6341008335cc94f1dba060680dbedafd59ba5d5ef8017d09ff8757cccdd0ec0c` before exposing an update-compatible APK.

When those secrets are unavailable, CI builds `-PmooncatPreview=true` with development signing. This produces a clearly named side-by-side Preview, never a replacement falsely labelled as a compatible update. Development signing is not a production distribution identity. Do not uninstall the working app to install Preview.

Keep keys, tokens, `local.properties`, compiled APKs and build caches out of git. Workflows build checked-in source; they do not import old source overlays or reassemble prebuilt APK chunks.

## Device verification

A compiled APK and unit tests cannot establish remote navigation, DRM compatibility, sustained streaming, or frame time on a physical TV. Check these on the target Fire TV before promoting a candidate to a stable release.

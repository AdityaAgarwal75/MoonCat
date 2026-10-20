# GitHub-first MoonCat workflow

MoonCat source, reviews, automated tests and APK builds should run in GitHub. The VPS hosts downloads and services; it is **not** the Android compilation machine.

## Normal changes

1. Create a short-lived branch from `main` and commit code, assets, and tests.
2. Open a pull request. [MoonCat Android CI](../workflows/mooncat-ci.yml) builds the TV debug APK, runs TV unit tests, and also builds the mobile debug APK if the mobile module is present.
3. Inspect the Actions logs and download the APK artifacts for installation tests.
4. Merge only after checks pass and the feature is reviewed. For playback/remote navigation, also test on a real device.

## Signed releases

The existing [MoonCat release build](../workflows/mooncat-build.yml) is responsible for signed TV releases. It needs **repository Actions secrets** `MOONCAT_KEYSTORE_BASE64`, `MOONCAT_KEYSTORE_PASSWORD`, and `MOONCAT_KEY_ALIAS`, containing the **existing** MoonCat signing identity. These values must never be committed or printed in workflow logs. Until those secrets and release signature are verified, use CI debug artifacts only for testing, not for upgrading installed Fire TV apps.

Releases must retain the package ID `dev.mooncat.tv` and increment `versionCode` while using the same signing key. A debug-signed APK cannot update a release-signed installation.

## Rules

- Never commit `local.properties`, `*.jks`, `*.keystore`, access tokens, browser profiles, or private API settings.
- Do not publish app artifacts from the VPS without verifying the build, package ID, version, and signing fingerprint.
- Keep mobile and TV APKs distinguishable and do not claim mobile feature parity before player/source integration and device tests.
- Use GitHub-hosted runners. Do not install a self-hosted Actions runner on the small VPS.
- Continue to use the VPS for persistent application hosting, databases, dashboards, and runtime tasks that cannot be performed inside a temporary Actions job.

## Migration note

The latest TV sidebar and mobile beta changes have been developed on the VPS but are **not fully synchronized with `main`**. Sync those source and binary assets using a secure authenticated GitHub connection, review the staged changes, and merge via PR before considering the GitHub repository authoritative for v1.0.2.

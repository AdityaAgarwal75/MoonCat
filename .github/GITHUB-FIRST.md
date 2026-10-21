# GitHub-first MoonCat workflow

The checked-in Kotlin source is authoritative. `.github/workflows/mooncat-tv.yml` validates studio data, runs the TV unit suite, assembles the release with vital lint, checks package and signing identity, verifies packaged catalogues, and uploads reports/APKs.

Normal changes use a branch and pull request. Candidate builds publish from `audit/tv-v1.0.6`. A source change is not complete until its corresponding workflow result is inspected. A green build does not replace physical Fire TV testing.

Read `docs/MOONCAT-BUILD.md` for signing and Preview behavior. Old workflows are archived as text under `docs/archive/workflows` to prevent source imports and prebuilt APK publishers from overwriting or bypassing reviewed builds. APKs belong in Releases, not split binary commits.

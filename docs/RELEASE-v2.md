# MoonCat 2.0.0-beta.16

Fire TV stability limits and Hindi-first playback, based on Beta 15.

- On devices with about 1 GB RAM, an app heap of 256 MB or less, or unknown RAM: disable parallel chunk downloading, experimental native performance mode, 4K autoplay and next-episode source preloading. Keep saved preferences intact; enforce managed buffering and disable the back buffer at runtime.
- Limit low-heap playback buffers to one fifth of the app heap, capped at 48 MB. This leaves more room for posters, playback decoders and navigation.
- Validate saved buffer durations before constructing a player to prevent invalid duration combinations from failing initialization.
- Give slower dubbed-source providers up to 2.5 seconds after the first result before falling back. Start immediately when preferred audio is advertised or discovery completes.
- Prefer supported audio tracks using language tags or clear container labels, including Hindi when the tag is missing. Preserve manual and resumed audio choices. Subtitle labels never establish a dub.
- Parse autoplay ranking keys once per candidate and reuse the language-label regex, reducing repeated string parsing during sorting. Offline replay of two real Torrentio lists and a 1,000-result stress case preserves the complete ranking for Hindi, English and Japanese.
- Default new profiles to Hindi; existing language settings are retained, with normal source fallback when no dub is found.

Signed in-place update: dev.mooncat.tv.v2, versionCode 20015. No uninstall required for compatible v2 installations.

Validation: release automation runs the full TV unit suite, release-script tests, asset validation, optimized APK assembly, vital lint, and APK identity/signature checks. Regression tests cover constrained devices, malformed buffer preferences, delayed Hindi discovery and bounded fallback. These changes address verified code paths; they do not establish the cause of every reported crash. Physical Fire TV playback, navigation, network behavior and memory use still require device testing. Dub availability depends on provider results and supported audio in each file.

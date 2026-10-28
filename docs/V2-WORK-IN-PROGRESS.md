# MoonCat 2.0 candidate

This branch is a work in progress, not a finished release. It begins at the 1.1.0 playback branch.

- Movie and episode stream searches are restricted to installed Torrentio and TorrentsDB addons. Catalogue, metadata and subtitle addons are retained, as are native personal-media requests. This does not change the configuration inside Torrentio: its own exclusions and limits still affect results.
- Autoplay considers HD episodes up to 4 GB, larger movies and opted-in 4K sources on 4K-capable devices. Manual source selection retains discovery results. A 100 GB safety ceiling applies during local torrent preparation.
- HTTP sample probes and up to four torrent preloads race concurrently. These are short readiness measurements, not a guarantee of sustained throughput or playable media. If no candidate is ready, the source picker is shown. Some containers and providers may not work with probes.
- Home rows are scheduled for display as each catalogue completes instead of waiting for the slowest outstanding catalogue. Actual responsiveness still needs device measurement.
- The built-in updater checks GitHub releases and validates the APK package, version and signing certificate before opening Android's installer. Android still requires confirmation. Version 2 uses a new package identity because the original signing key was not available to the 1.1 release.

## Release gate

Do not publish or install this beta as a durable update channel using an ephemeral CI debug key. Provision and securely retain a dedicated v2 release keystore and configure GitHub Actions signing secrets first. Every future v2 release must use that same key and application ID; otherwise Android cannot update in place. The workflow deliberately only uploads build artifacts; it does not publish a release. Physical Fire TV playback, source availability and home-screen performance need device testing.

Artwork was generated with the built-in image generator from prompts requesting a cute cream kitten on a lavender moon in a detailed anime slice-of-life style, plus a cozy rainy-night room and vertical poster. See `branding/v2/`. `scripts/package_v2_branding.py` resizes the generated art and renders the exact wordmark.

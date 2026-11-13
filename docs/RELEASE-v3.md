# MoonCat 3.0.0-alpha.2 candidate

The existing TV screens, navigation, artwork, package identity and playback engines remain. This candidate rebuilds source-selection and recovery ownership around the latest Beta17/audit source, rather than the older Beta15 on main.

- Manual source list: show valid 1080p, 720p and SD addon sources, including files below 1 GB, while keeping the 6 GB (decimal) maximum for known sizes. 4K, camera and above-6-GB sources remain excluded. Sources with unknown size can remain visible for manual selection.
- Autoplay: permit 1080p releases of any positive known size through 6 GB, including sub-1 GB files. The 1080p preference remains unchanged; 720p and SD files remain available manually.
- Hindi audio first for movies and series, then the known original language. Anime prefers Hindi, English, then original. Both source ranking and default container track choice use this policy; explicit manual audio choices retain precedence. Recognize anime IDs even before genres load.
- One ranking policy handles initial play, next episode and recovery. A torrent advertised with zero seeds is demoted so dead dubbed entries cannot consume every retry before a viable original-language option.
- Interactive addon searches have an8-second total budget including queue/retry. Initial/next-episode source search has a10-second ceiling; native torrent preparation is bounded to10seconds. These stage limits are not a guarantee of a10-second click-to-first-frame time.
- Recovery owns resolver/native/player initialization jobs until they finish, retains failures during preparation, limits retries, and preserves the playhead. Manual choices invalidate pending recovery. Releasing the player cancels old watchdogs and initialization.
- All installed compatible secure providers may respond concurrently. Torrentio remains supported; no unverified extra provider is installed.

Native personal-media and YouTube adapters negotiate formats separately and currently bypass addon metadata filtering; verify their format constraints independently. Source labels do not prove a dub or speed. Physical FireTV startup, sustained playback, audio and remote navigation need testing before stable promotion.

## Installation and signing
Version30001 /3.0.0-alpha.2 retains dev.mooncat.tv.v2 and the existing v2 signing configuration. The version number does not require renaming the package. The V3 branch workflow uploads a validated signed candidate if trusted signing secrets exist, otherwise an isolated dev.mooncat.tv.v2.preview build. Do not uninstall a working app to sideload a differently signed update. A successful signed V3 branch build can publish a prerelease update.

## Audit evidence
See V3-SOURCE-INVENTORY.csv for the source inventory, the design and plan under docs/superpowers, and the validation ledger in V3-VALIDATION.md. Whole-tree structural inventory is separate from the deeper playback-path review. This is a candidate, not a claim that every feature has been exercised on a TV.

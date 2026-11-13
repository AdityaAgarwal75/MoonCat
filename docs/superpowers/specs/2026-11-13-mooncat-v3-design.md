# MoonCat V3 playback backend

## Intent and constraints
The owner delegated backend design and implementation to make playback, source selection and autoplay reliable while preserving all existing screens. Kotlin, Compose, Media3 and the native torrent engine remain appropriate for Android TV. Keep the dev.mooncat.tv.v2 identity and signing contract for upgrade continuity; version 3 does not require a package rename. No server migration or new paid service is required.

## Baseline and audit
main points to Beta 15, not the latest app. Start at b28173a (Beta 17 plus the numeric-resolution fix on improve/superpowers-beta18). The application has 953 main Kotlin files and 352 test files. Inventory the whole tree and trace critical paths: repository/cache → StreamScreenViewModel → selection → player/torrent adapters → recovery, plus next-episode selection, load control, settings, lifecycle and release tooling. This is a structural audit and focused source review, not a claim that every line of 953 files was manually inspected.

Confirmed structural concerns: ranking is duplicated across rank/instantOrder/readinessOrder; quality precedes torrent availability; repeated ranking occurs on the UI coroutine after the grace window; recovery does not own the asynchronous source preparation it initiates; an empty exhausted discovery can leave selection waiting until an unrelated watchdog; failure budgets have no explicit fresh-session reset.

## Approaches
1. Replace Kotlin and move playback behind a remote service: rejected; it cannot repair local decoder/lifecycle ownership and adds network and operational cost.
2. Patch each screen independently: rejected; ranking and cancellation would continue to diverge.
3. Centralize pure selection policy and request ownership behind existing interfaces: selected by implementer under the owner's delegated design authority. Existing UI and protocol/decoder adapters remain the outer boundary.

## Design
One precomputed ranking policy drives initial autoplay, next episode and recovery. Reject unplayable/external entries. Updated owner requirements: accept only 1080p addon releases with known sizes from 1,000,000,000 through 6,000,000,000 bytes. Six GB is the implementer-selected ceiling to include roughly five GB releases while excluding 8–10 GB. Hindi precedes the known original language for movies/series; anime uses Hindi, English, original. Missing original-language metadata is not treated as proof of a particular language. Native media-server/YouTube adapters remain protocol-specific; verify their negotiated tracks on-device. Within an audio tier, prefer sources with known positive torrent availability or resolved direct routes over unknown availability, then explicitly zero-seeded torrents; among viable HD torrents prefer availability before resolution. Metadata is only a hint, never proof of throughput. Keep manual results visible.

Metadata selection uses a bounded 300ms provider collection window, evaluates ranking off the UI thread, and follows discovery completion even if candidates disappear. Recovery owns a generation token: late completion or cleanup from an older request cannot mutate or release a newer request. Manual source/episode changes invalidate pending recovery. Recovery awaits source preparation instead of dropping ownership immediately, with bounded waiting and explicit terminal errors. Reset failure history for a manual choice, preserve it for automatic retry. Interactive provider work has an 8 second budget including queueing and retry. Initial source selection retains a 10 second hard watchdog; native preparation has a 10 second deadline. These are per-stage ceilings and not a measured end-to-end startup guarantee. Cancel player watchdogs when releasing a player.

## Verification and limits
Write regression tests before code. Run focused real Kotlin policy/coroutine tests, existing Android unit tests, release-script tests and asset checks; assemble the preview APK if the environment resolves dependencies. No claim about sustained swarm throughput or Fire TV frame times until device testing. Do not publish a stable release without signed build and device evidence.

## Latest owner clarification
The 720p availability fallback in generic ranking regression tests is not enabled at the V3 application boundary: PlaybackPreferences rejects 720p, 4K, unknown resolution, unknown addon size, sub-1GB and over-6GB sources before autoplay. Seed counts are advertised hints, never speed tests. All installed compatible secure providers remain concurrent; no unverified new endpoint is added.

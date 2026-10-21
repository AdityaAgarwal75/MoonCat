# MoonCat 1.0.6 TV candidate

- Retains the 1.0.5 provider and Hindi playback improvements.
- Keeps SD episode sources as a fallback after HD, instead of silently removing them.
- Repairs bare IMDb episode requests when season/episode are supplied.
- Bounds provider concurrency and timeout; skips unsupported metadata fallback requests.
- Shows source choices before waiting for readiness probes, preserves pagination, and keeps unavailable providers visible until retry.
- Uses episode-specific saved-source keys and limits the in-memory search cache to four sessions.
- Cancels earlier News connection requests when a later channel is selected.
- Compiles source-policy patterns once; reduces simultaneous poster fetch/decode work and removes poster crossfades.
- Adds the official YouTube catalogue and a Comet source connector. Availability depends on the provider and region.
- Updates repository branding and consolidates build instructions.

This is a candidate, not a claim that every bug is fixed. Device playback, remote navigation, and interface speed must be measured on real Fire TV hardware. Read the workflow and test results attached to this release for actual validation status.

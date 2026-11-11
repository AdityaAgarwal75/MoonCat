# MoonCat 2.0.0-beta.17

- Autoplay picks the highest advertised seeder count within the preferred audio and resolution tier. The old 3–3.5 GB size preference no longer beats a better seeded file. Retain the 4 GB cap and exclusion of 4K and camera releases.
- Read counts from provider names, titles and descriptions, including comma-separated counts, emoji variation selectors and K/M abbreviations. Unlabelled numbers such as years, resolutions and file sizes are not seed counts.
- Collect provider results for at most 300 ms after the first nonempty result, ending earlier on completed discovery. Start the chosen source through the normal player, without a speed-test or torrent race. Later provider responses may still arrive after selection.
- Torrent playback starts at a one-second media buffer target, resumes after stalls with eight seconds, and tries to retain 60–90 seconds ahead subject to the existing device memory cap. Actual startup still depends on provider discovery, peers, metadata and decoding.
- For a torrent that stops advancing its playable buffer for 20 seconds after its first frame, try the next candidate at the current playback position instead of seeking forward past buffered content. Never recover while paused. Recovery remains bounded to four alternative attempts.
- Recovery uses the same popularity ranking and size limit as initial autoplay.

Version code 20016; package and signing identity follow the existing v2 release pipeline. CI runs the TV unit suite, release-script tests, packaged-asset checks, optimized APK assembly and signature verification. Physical Fire TV startup and buffering remain to be measured; seeder counts do not establish sustained download speed.

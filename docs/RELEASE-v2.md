# MoonCat v2.0.0-beta.4

This update uses Torrentio for addon streams, hides identified 4K sources and sources with a reported size above 4 GB, and defaults new theme settings to AMOLED. Torrent file size is checked when available. Sources without known size or resolution may still appear. Existing theme choices are preserved.

Autoplay tests well-seeded candidates sooner and falls back to the source list after the discovery timeout. Startup time still depends on the network and available peers.

Addresses a possible buffer cleanup race when playback sessions close.

Playback readiness checks, home catalogue improvements, and the v2 artwork are included. Playback depends on the source, available peers, and network. This beta has not been verified on every TV device.

Earlier pre-v2 MoonCat installs use a different signing identity. This release updates an existing v2 installation and requires confirmation in the Android installer.

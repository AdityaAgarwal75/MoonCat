# MoonCat v2.0.0-beta.10

This release changes only the Beta 9 autoplay metadata ranking and version number. It keeps the same selected-cat branding, parallel configured addon discovery, preplay-probe-free playback path, 4 GB cap, and manual source switching.

Within the preferred language, 1080p comes first. Among 1080p results, reported file sizes from 3.0 to 3.5 GB are preferred. Advertised seed counts then rank releases within that band. Other 2.5 to 4 GB files follow, then smaller files, then unknown sizes. Release labels and larger size break remaining ties. There is no provider-wide view counter and a seeder count does not prove speed. The choice happens when the first addon results arrive rather than waiting for every addon, so it cannot guarantee the best result across addons that respond later.

Torrent metadata, peers, and media buffering still take time after the selection. Measure Play to first rendered frame and failed starts on the same Fire TV title and network before treating this as a speed improvement. ThePirateBay+ public endpoint returned 502 during the Beta 9 check; it can be disabled in Addons if unavailable.

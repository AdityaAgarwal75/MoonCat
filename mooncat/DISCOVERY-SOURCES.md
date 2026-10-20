# TV discovery metadata

Studios use a packaged, compressed metadata index so opening the page does not depend on a remote catalogue service. The index contains IMDb IDs, titles, types and release years; posters are loaded by IMDb ID from Metahub, using the same focusable card as Home. Opening a poster routes to MoonCat's existing details/episodes/player flow.

The 2026-10-20 snapshot contains 11,655 entries across eleven collections. Entries can appear in more than one studio. Coverage is **not** a guarantee of a complete filmography or a playable source. Shorts are included; individual TV episodes and seasons are excluded. Some metadata may misclassify a programme. The Wizarding collection includes the eight Harry Potter films and three Fantastic Beasts films.

Metadata source: Wikidata Query Service, CC0 structured data. Company membership uses production company (P272), distributor (P750), broadcaster (P449), and curated franchise membership (P179/P1080). HBO includes its broadcast/co-production catalogue rather than implying it produced every title. Sony means Columbia Pictures. Star Wars is a franchise filter on Lucasfilm, not all Lucasfilm productions.

Logos: Wikimedia Commons original studio marks, downsampled to WebP without cropping. Channel logos and broadcaster CDN URLs were located through https://github.com/iptv-org/iptv and https://iptv-org.github.io/api/logos.json. News24's replacement CDN was verified against its official https://news24online.com/live-tv/ player. Artwork and logos remain their owners' property.

## Stream providers

- Torrentio: existing configured installation is retained.
- MediaFusion: https://github.com/mhdzumair/MediaFusion; https://mediafusion.elfhosted.com/manifest.json. Added once as stream discovery only, without importing its home catalogues. Availability and any debrid configuration depend on the provider.
- Comet: https://github.com/g0ldyy/comet. Manifest was reachable, but its unconfigured stream endpoint returned HTTP 403; not installed as a working default.
- Peerflix and Stremio Jackett public instances failed live checks; not installed.

Provider lookups are concurrent. Selection waits at most 2.5 seconds after lookup starts before evaluating available results (providers may continue responding), then performs at most seven concurrent 64 KiB HTTP probes, each with a 1.5-second deadline. HTTP sample readiness is not sustained bandwidth. Torrent-only candidates first use release quality/language and advertised seed counts, then race up to four verified 64 KiB preloads within the existing shared torrent engine. The native race has a 4.5-second per-candidate deadline, keeps the winning metadata/cache, and stops losing routes/torrents. It does not interrupt an already-playing torrent during language switching; that path verifies candidate files sequentially. Native source startup still requires Fire TV testing.

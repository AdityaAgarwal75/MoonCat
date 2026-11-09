# MoonCat 2.0.0-beta.13

Manual source control during loading and playback.

- A compact right-side card shows the selected addon, source name and available release details while loading or buffering. It also appears with player controls during playback.
- Choose **Change source**, or press **RIGHT** or **MENU** while loading, to open the existing source picker immediately.
- The picker now renders above the loading screen. Sources already found remain selectable while slower addons continue searching, instead of being hidden behind a spinner until every addon finishes.
- The panel identifies the current source and retains its existing selected-row indicator. It remains available if the current stream fails while browsing.
- Opening the picker cancels pending automatic source recovery so it does not override a manual choice. Selecting an alternative preserves the current or pending resume position and uses the existing stream-switch cleanup.
- Closing the picker while a torrent is still preparing returns to loading rather than exiting merely because its local video URL is not ready yet.

All photos, branding, provider configuration and Beta 12 playback policies are retained. This is a signed update for `dev.mooncat.tv.v2`, versionCode 20012. Physical Fire TV focus/navigation and real network playback require device testing; automated checks do not measure those outcomes.

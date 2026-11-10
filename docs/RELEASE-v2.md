# MoonCat 2.0.0-beta.14

Source controls only when loading or buffering.

- The right-side selected-source card now disappears when loading finishes. Opening normal playback controls or pausing the video does not bring it back.
- If playback buffers again, the card returns so you can inspect the selected source and choose **Change source**. It disappears again when buffering ends.
- **RIGHT** and **MENU** still open the source picker while loading. The picker remains above the loading screen and supports sources already found while slower addons continue searching.
- Manual switching retains Beta 13's resume-position handling and protection against automatic recovery overriding a manual choice.

All photos, branding, providers and playback selection policies are unchanged. This restores the Beta 13 source controls with the loading-only visibility correction. Signed update for `dev.mooncat.tv.v2`, versionCode 20013.

Regression coverage includes playback with controls open, paused playback, initial loading and rebuffering. Physical Fire TV navigation and real network playback still require device testing.

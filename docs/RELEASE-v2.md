# MoonCat v2.0.0-beta.8

Implements the first startup changes from the Astra audit on top of the Beta 7 source candidate.

The detail page starts the shared source search after a short dwell for its exact movie or next episode. Play joins that repository session. Leaving details, changing its episode target or changing profile cancels the speculative collector. Builds can disable this experiment with -PmooncatDetailWarmup=false for device comparisons.

The verified winning torrent route is retained for up to 15 seconds and transferred into actual playback for the same hash, requested file index and filename. Other probe routes are stopped. Expired or mismatched requests prepare a fresh route. This avoids a second prepareStream call for a verified winner. Existing real-player source recovery now uses discovered alternatives before starting another lookup.

Includes the prior candidate's configured-addon support, diverse source probes, measured HD preference, and reported size/resolution filtering.

The audit's Media3 prepared HTTP MediaSource experiment and physical Fire TV timing comparison are still pending. The external-player 5 MiB preload gate remains pending a measured experiment. These source changes do not establish instant playback or measured startup improvements.

Publication requires the complete unit suite, release build and vital lint, signing verification and packaged catalogue checks. Device playback and lifecycle validation must follow before stable promotion.

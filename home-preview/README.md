# MoonCat Home preview
Standalone native Android TV app with exactly YouTube, Hotstar and MoonCat.
No network, accessibility, boot receivers, background service or HOME intent filter.
Amazon launcher remains enabled; the remote Home button stays unchanged.
Test by launching MoonCat Home from the apps list. Exit with Home or Back.
Rollback: uninstall only dev.mooncat.home. No system package or setting is modified.
Uses installed app icons and a clock that pauses when the activity is not visible.
Build output is an Actions artifact, not a MoonCat media-app update.

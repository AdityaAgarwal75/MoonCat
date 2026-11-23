# MoonCat mobile and TV implementation plan

- Verify Torrentio manifest and a hash-only response. Match the TV engine's file selection and size checks in the Android player. Keep direct HTTPS playback.
- Use the TV launcher artwork for Android's icon and in-app brand. Make the phone artwork, actions, navigation, and episode list touch-first. Validate at compact portrait sizes.
- Pair an open Fire TV app on the same Wi-Fi with a QR code. Send the selected title and position over an authenticated, short-lived request. Let TV choose a source locally.
- Reuse the existing account backend if its signing, device-link and sync interfaces are available. Verify the currently distributed TV APK's configuration before claiming account support.
- Build, sign, and inspect APKs independently. Verify the exact source commit and state any device tests still outstanding.

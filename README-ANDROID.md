# TikTokLiveTTS DEMO

Public Android demo build of TikTokLiveTTS.

## DEMO mode
- Skips the login/license screen for demonstration only.
- Displays a visible DEMO label.
- Does not grant production license entitlements or admin access.
- TikTok LIVE chat ingestion is not implemented in this demo scaffold.

## Mandatory updates
The app checks the latest GitHub Release on every launch:
`https://github.com/kelio554/TikTokLiveTTS-Demo/releases/latest`

When a release tag with a higher integer version is found (for example `v3` while the app has versionCode 2), the app blocks normal use and offers the APK download. Android still requires the user to approve installation; silent installation is not available to an ordinary app.

## Publish an update
1. Increase `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Push a tag matching the new integer version, e.g. `v3`.
3. GitHub Actions builds and attaches `TikTokLiveTTS-demo.apk` to the Release.
4. Installed demo apps detect that release on next launch.

Do not put API keys, signing keys, GAS secrets, or admin credentials in this public repository.

# TikTok Live TTS (Android)

Native Android app that connects to a public TikTok LIVE by username, receives chat comments, and reads them aloud using the Android device's installed Text-to-Speech engine. Speech text is processed locally by Android TTS; the app does not send comments to a cloud voice-generation API.

## App flow

1. On every app launch, validate the cached license key with the configured Google Apps Script deployment. If the key is missing, ask for it. If validation fails, keep the app locked.
2. Cache a successfully validated key in the app's private storage so the user does not have to re-enter it on every launch.
3. Enter the TikTok LIVE username and connect.
4. Read incoming comments automatically with Android TextToSpeech. The reader can be turned off in Settings.

## Build APK

GitHub Actions builds a debug APK on every push to `main` and on manual workflow dispatch. Open the **Actions** tab, select **Build Android APK**, then download the `TikTokLiveTTS-debug-apk` artifact.

Local build (Android SDK + JDK 21 required):

```bash
./gradlew assembleDebug
```

## TikTok LIVE transport

The app uses an Android-native OkHttp WebSocket/WebCast implementation and protobuf decoding adapted from the open-source TakTik project (Apache-2.0). TikTok does not provide a public official LIVE chat API; this protocol is unofficial and may change. The current signing integration uses Eulerstream for TikTok WebCast request signing. This is separate from speech generation; comment-to-speech stays on the Android device.

## License validation

The Google Apps Script endpoint is configured in `LicenseApi.kt`. The app sends a POST JSON request with `action: validateKey`, `key`, `licenseKey`, `apiKey`, `deviceId`, and app/version metadata. The response must be JSON and indicate validity with `valid`, `success`, `active`, or a `status` of `valid`, `active`, `success`, or `ok`. The app fails closed if the endpoint is unreachable or returns an unrecognized response.

The deployed script must implement `doPost(e)` for this request. A `doGet` handler is not required.

## Attribution

The live analytics/WebCast foundation is adapted from [TakTik](https://github.com/mohamaddkhalill/taktik), Copyright Mohamad Khalil, Apache License 2.0. See `LICENSE` and `NOTICE`.

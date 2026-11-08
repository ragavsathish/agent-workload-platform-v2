# Native client qualification

## Android build and launch

- Built the neutral Android wrapper with Flutter 3.47.7, Android SDK Platform
  36, Build Tools 36.0.0, NDK 28.2.13676358, Gradle 9.3.1, AGP 9.1.0, and
  OpenJDK 17.0.21.0.3:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home PATH=/opt/homebrew/opt/openjdk@17/bin:$PATH ./tool/flutterw build apk --debug`.
- Installed and launched the APK on the existing `Pixel_8_API_36` emulator.
  The launch reached `com.awp.v2.voice_agent_client/.MainActivity`; no fatal
  Android runtime exception was recorded in the captured log window.
- The package requests `INTERNET` and `RECORD_AUDIO`; it does not request
  `CAMERA`. After launch, Android reported `RECORD_AUDIO` as not granted.
- APK SHA-256:
  `99e68ac0da2b10af184f7b2c6ae8f5dd5b2e7fcceef431fdbe2b08991444ac2e`
  (156,146,427 bytes).

## Remaining native evidence

- The run proves Android compilation and launch and confirms that microphone
  permission is not granted automatically at launch. It does not exercise the
  voice-start gesture or observe the operating-system microphone prompt; the
  private v2 voice origin has not been qualified. Android prompt timing remains
  open.
- iOS is unqualified. Xcode 27.1 has iOS and simulator SDKs, but no simulator
  runtime is installed. CocoaPods is absent. Installing CocoaPods with the
  system Ruby 2.6.10 failed because Xcode's macOS SDK does not contain the Ruby
  headers needed to build the `nkf` native extension. No iOS build or device
  launch was completed.

This is local build evidence only. It does not qualify a private tailnet owner
journey or external deployment.

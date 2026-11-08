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

## iOS Simulator build (slice 28)

See the [qualification slice](../../docs/plan/28-ios-owner-client/plan.md) for
the behavior contract and evidence boundary.

- Built with Flutter 3.47.7 and Xcode 27.1 (build 27A9275):
  `./tool/flutterw build ios --simulator --no-codesign` from
  `impl/runtime/products/voice-agent/apps/client`.
- The build completed successfully in 132 seconds and produced
  `build/ios/iphonesimulator/Runner.app` (152 MB). Its `Runner` executable is a
  universal Mach-O containing arm64 and x86_64 architectures; SHA-256:
  `314a9f7f36a03ecb91895035e8546e31f50004bcd4e96f2241d0c31f931663d7`.
- Xcode resolved the iOS plugins through the existing Swift Package Manager
  integration; CocoaPods and system Ruby changes were not needed. The generated
  `ios/Runner.xcworkspace/xcshareddata/swiftpm/Package.resolved` pins
  CocoaAsyncSocket 7.6.5 at revision
  `dbdc00669c1ced63b27c3c5f052ee4d28f10150c`.
- The built app plist contains `NSMicrophoneUsageDescription` with the
  voice-start explanation and no `NSCameraUsageDescription` key.
- `xcrun simctl list runtimes` reports no installed runtimes. Therefore this
  proves simulator-target compilation only; iOS launch, voice-start prompt
  timing, and an authenticated owner journey remain unqualified.

This is local build evidence only. It does not qualify a private tailnet owner
journey or external deployment.

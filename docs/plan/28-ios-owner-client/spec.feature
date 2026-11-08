Feature: iOS native owner client qualification
  The owner uses the same private workspace through a supported iOS shell.

  @ios-owner-client-001
  Scenario: Build the iOS Simulator target
    Given the pinned Flutter SDK and Xcode iOS Simulator SDK are installed
    When the iOS client is built without code signing
    Then the build compiles for the iOS Simulator target
    And the qualification report records its toolchain and result
    And no device launch or owner journey is inferred from compilation

  @ios-owner-client-002
  Scenario: Keep microphone permission scoped on iOS
    Given the iOS shell presents the configured private workspace
    When spoken interaction requests microphone media
    Then the shell has a microphone usage description
    And microphone access follows the native permission timing rule
    And camera access is not requested

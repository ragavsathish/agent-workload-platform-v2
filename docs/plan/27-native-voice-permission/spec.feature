Feature: Native microphone permission timing
  The native shell requests microphone access only in response to the private
  workspace starting spoken interaction.

  @native-voice-permission-001
  Scenario: Retry loading the workspace without requesting microphone access
    Given the native workspace failed to load
    When the owner retries loading it
    Then the workspace reloads
    And the operating system microphone permission is not requested

  @native-voice-permission-002
  Scenario: Grant a page microphone request after voice start
    Given the owner starts spoken interaction in the trusted workspace
    When the workspace requests microphone media
    Then the shell requests operating system microphone access
    And microphone media is granted only when access is granted

  @native-voice-permission-003
  Scenario: Reject camera or mixed media requests
    Given the trusted workspace requests camera or mixed media
    When the native shell evaluates the request
    Then the request is denied
    And microphone permission is not requested

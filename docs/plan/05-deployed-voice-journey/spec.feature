# Sources: docs/wiki/deployed-voice-journey.md and raw/v1/voice-testing.md.
Feature: Deployed owner voice journey
  The owner's live speech and selected conversation have one observable result.

  @voice-001
  Scenario: Final speech reaches the selected conversation and returns speech
    Given an authenticated owner has selected a saved conversation
    When the owner speaks one final utterance over a live media connection
    Then recognition yields that utterance as one accepted turn
    And the turn is dispatched once to the selected conversation
    And audible assistant media and safe transcript identify the same answer

  @voice-002
  Scenario: A spoken turn survives service restart
    Given the owner completed a spoken turn in a selected conversation
    When the conversation service starts fresh and the owner resumes that conversation
    Then its identity and safe turn history are visible through public controls
    And the completed utterance is not dispatched again

  @voice-003
  Scenario: Interruption and reconnect preserve routing safety
    Given a spoken turn is streaming to its selected destination
    When the live connection ends before the outcome is known
    Then interruption is requested at that destination
    And the turn is settled or inspectably uncertain
    And reconnecting does not dispatch that turn again

  @voice-004
  Scenario: An attached task receives the next spoken turn
    Given an eligible owned task is attached to the selected conversation
    When the owner speaks a final utterance over a live media connection
    Then the accepted turn reaches the task's own conversation
    And the owner receives its safe spoken and transcript reply
    And the orchestrator's saved history does not claim the task turn

  @voice-005
  Scenario: Missing speech capability fails visibly
    Given the owner has a selected conversation and a live media connection
    When recognition or speech output is unavailable for a final utterance
    Then the owner sees a bounded failure or inspectable uncertainty
    And no successful spoken turn is reported without response media

  @voice-006
  Scenario: Public authority protects the media journey
    Given an owner has a selected conversation
    When another caller attempts to open or read that owner's voice journey
    Then no selected history or turn capability is exposed

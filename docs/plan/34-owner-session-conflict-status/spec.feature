Feature: Preserve owner session command conflict status

  @voice-conflict-status-001
  Scenario: A held compaction rejects a new turn as a conflict
    Given the owner has an uncertain compaction holding the session lock
    When the owner submits a typed turn
    Then the route returns HTTP 409
    And the model is not called
    And the response contains only a bounded safe conflict detail

  @voice-conflict-status-002
  Scenario: A routing transition rejects a turn as a conflict
    Given task routing is changing for the owner
    When the owner submits a typed turn
    Then the route returns HTTP 409
    And the model is not called

  @voice-conflict-status-003
  Scenario: A provider failure remains an upstream failure
    Given the owner turn was accepted by Grain
    When the Pi provider fails during execution
    Then the route returns HTTP 502
    And the outcome remains inspectable as uncertain when settlement is unknown

  @voice-conflict-status-004
  Scenario: A stream reports a pre-dispatch conflict safely
    Given the owner session is locked by an uncertain command
    When a streamed turn is submitted
    Then the stream emits an error event with conflict status 409
    And the model is not called

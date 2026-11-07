# Sources: docs/wiki/live-session-integration.md and raw/v1/manifest.md.
Feature: Live owner conversation integration
  The owner's public controls and live voice connection use one selected conversation.

  @live-001
  Scenario: Public selection and history use the recorded conversation
    Given the owner has completed a turn in a saved conversation
    When the owner starts a fresh conversation and resumes the saved one through public controls
    Then the catalog and active transcript identify the resumed conversation
    And historical reads do not change selection
    And the persistent tool environment has the same identity

  @live-002
  Scenario: Typed and spoken turns reach one selected conversation
    Given the owner has a selected conversation without a task attachment
    When the owner sends a typed message and then speaks a final turn over a live connection
    Then each latest actionable prompt is dispatched once in order to that conversation
    And safe assistant observations appear in the public transcript and owner response

  @live-003
  Scenario: Streaming interruption keeps the selected conversation
    Given an owner spoken turn is streaming from the selected conversation
    When the stream disconnects or the owner stops the turn
    Then interruption is requested at that turn's destination
    And the turn settles or remains inspectably uncertain
    And the selected conversation accepts a later distinct turn

  @live-004
  Scenario: Task attachment routes the next turn to the task
    Given the owner has a selected conversation and an eligible owned durable task
    When the owner attaches that task and sends another turn
    Then that prompt reaches the task's own conversation
    And its reply is visible in the live public transcript
    And the orchestrator's saved history does not claim the task turn

  @live-005
  Scenario: Selection and disconnection release temporary task routing
    Given an owned durable task is attached to the selected conversation
    When the owner changes selection or ends the voice connection
    Then the attachment is released
    And the task remains inspectable under its original identity
    And a later turn reaches the selected orchestrator conversation

  @live-006
  Scenario: Public reads recover after a process restart
    Given the owner resumed an older conversation and completed a live turn
    When the conversation service restarts
    Then the same selected identity, catalog, history, and safe transcript are visible
    And no old turn or temporary task connection is replayed

  @live-007
  Scenario: Retry after a lost answer cannot repeat an effect
    Given an owner turn was accepted and its external outcome is uncertain
    When the same turn identity and input are retried through the public path
    Then the recorded decision and uncertainty are returned
    And no second external prompt is dispatched

  @live-008
  Scenario: Credentials and malformed input do not expose a session
    Given an owner has saved and active conversations
    When a foreign caller or invalid service credential requests their controls or history
    Then no content, selection, or turn capability is exposed
    When an owner submits an invalid or oversized turn
    Then the request is boundedly refused without an external prompt

  @live-009
  Scenario: Context maintenance follows the selected live conversation
    Given the selected conversation has completed a turn with usage
    When the owner reads context and requests compaction through public controls
    Then the usage reflects that conversation and completed maintenance
    And a concurrent turn does not change its destination during maintenance

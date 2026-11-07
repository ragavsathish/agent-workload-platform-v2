# Sources: docs/wiki/session-orchestration.md and raw/v1/manifest.md.
Feature: Current conversation orchestration
  The owner selects one persistent conversation for voice and typed turns.

  @session-001
  Scenario: A fresh conversation preserves prior history
    Given the owner has an active conversation with a completed turn
    When the owner starts a fresh conversation
    Then a different conversation identity becomes active
    And the prior conversation remains readable without becoming active

  @session-002
  Scenario: A saved conversation can be resumed
    Given the owner has a saved conversation and a different active conversation
    When the owner resumes the saved conversation
    Then its identity becomes active
    And its display-safe history becomes the live transcript

  @session-003
  Scenario: Selection and history survive a fresh start
    Given the owner resumed an older saved conversation and completed a turn
    When the orchestration starts again
    Then the same conversation identity is selected
    And its public history is visible without replaying the turn

  @session-004
  Scenario: A missing conversation cannot replace selection
    Given the owner has an active conversation
    When the owner tries to resume an unknown identity
    Then a bounded missing-conversation answer is returned
    And the active identity and transcript remain unchanged

  @session-005
  Scenario: Busy selection is refused
    Given a turn or maintenance operation is in progress
    When the owner tries to change the selected conversation
    Then the change is refused
    And the in-flight operation keeps its original identity

  @session-006
  Scenario: A selection retry converges after a lost response
    Given a conversation selection was accepted but its answer was lost
    When the owner retries the same command identity and input
    Then it returns the one selected identity
    And no additional conversation is created
    When that identity is reused with different input
    Then the conflicting command is refused

  @session-007
  Scenario: Voice and typed turns share the selected conversation
    Given an active conversation with no task attachment
    When the owner submits a voice turn followed by a typed turn
    Then both turns appear in that conversation in order
    And no second conversation identity is created

  @session-008
  Scenario: A turn uses only its latest actionable message
    Given a bounded conversation request ending in an owner message
    When the owner submits the turn
    Then the latest actionable message is sent once to the selected conversation
    And earlier supplied history is not submitted again as new turns

  @session-009
  Scenario: Public transcript excludes private material
    Given a turn has assistant text, private reasoning, and a tool call
    When the owner reads the active or historical transcript
    Then bounded user and assistant text and a safe tool name and status are visible
    And private reasoning, tool input, and tool output are absent

  @session-010
  Scenario: Stopping a turn retains selection
    Given the owner has a turn in progress
    When the owner stops that turn or its stream disconnects
    Then an interruption is requested for that turn
    And the selected conversation remains active for a later turn

  @session-011
  Scenario: Context maintenance is serialized with turns
    Given the owner requests context compaction while a turn is in progress
    When the turn settles and maintenance runs
    Then the context usage view reflects the settled maintenance
    And no turn is routed to a different conversation during maintenance

  @session-012
  Scenario: An attached task receives following turns
    Given the owner has selected an orchestrator conversation
    When the owner attaches an owned durable task and submits a turn
    Then that turn is routed to the task's own conversation identity
    And the selected orchestrator conversation identity is unchanged
    And the task reply is visible in the live public transcript
    And the orchestrator's saved history does not claim the task turn

  @session-013
  Scenario: Selection releases a task attachment without changing task identity
    Given an owned durable task is attached
    When the owner starts or resumes an orchestrator conversation
    Then the task attachment is released
    And the durable task remains inspectable under its original identity

  @session-014
  Scenario: Only the owner can observe or control conversations
    Given one owner has saved and active conversations
    When another caller lists, reads, resumes, or submits a turn to them
    Then no conversation content or selection capability is exposed
    And the persistent tool environment is not granted through that caller

  @session-015
  Scenario: Ending voice contact releases temporary task routing
    Given the owner has an active orchestrator conversation and an attached task
    When the voice connection ends
    Then the task attachment is released
    And a later unattached turn uses the same selected orchestrator conversation

  @session-016
  Scenario: The owner catalog keeps its bounded public shape
    Given the owner has more than 30 saved orchestrator conversations
    When the owner lists conversations through the public contract
    Then at most 30 summaries are returned with the active conversation first
    And each summary contains identity, title, update time, message count, and active marker
    And no host path or private transcript content is returned

  @session-017
  Scenario: A turn retry cannot repeat an uncertain external effect
    Given an accepted owner turn has an uncertain outcome after a lost response
    When the owner retries the same turn identity and input
    Then the recorded turn decision and uncertain outcome are returned
    And no second turn is dispatched
    When that identity is reused with different input
    Then the conflicting turn is refused

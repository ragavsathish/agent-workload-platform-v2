Feature: Private owner workspace
  The owner uses one private workspace to continue conversations and bounded
  work while the server remains authoritative for identity and outcome.

  @owner-client-001
  Scenario: Open the owner's workspace
    Given the owner has saved conversations and durable tasks
    When the owner opens the workspace
    Then the selected conversation and recent catalog are visible
    And only that owner's conversations and tasks are shown

  @owner-client-002
  Scenario: Start and resume conversations
    Given the owner has a saved conversation
    When the owner starts a new conversation and later resumes the saved one
    Then each conversation retains its own identity and safe history

  @owner-client-003
  Scenario: Send a typed turn
    Given the owner has selected a conversation
    When the owner submits a typed turn
    Then the answer and safe transcript are shown
    And the settled turn is available after reload

  @owner-client-004
  Scenario: Speak and interrupt a turn
    Given the owner has selected a conversation
    When the owner starts a spoken turn and interrupts it before completion
    Then the active turn stops
    And the partial response is not shown as a complete answer

  @owner-client-005
  Scenario: Create and inspect a durable task
    Given the owner has an eligible workspace
    When the owner submits bounded work
    Then one task identity is shown with its status and bounded progress

  @owner-client-006
  Scenario: Task routing remains server controlled
    Given the server records an owner-authorized task attachment
    When the owner sends a later turn and the server releases the attachment
    Then the server routes and releases turns according to its recorded state
    And the client exposes no direct attach or detach control until the Pi and Grain effects are integrated

  @owner-client-007
  Scenario: Retrieve a task artifact separately
    Given a completed task has an artifact reference
    When the owner requests that artifact
    Then the artifact is returned only to that owner
    And a retrieval failure leaves the task completed

  @owner-client-008
  Scenario: Maintain context explicitly
    Given the selected conversation reports that maintenance is available
    When the owner requests context maintenance
    Then the owner sees its queued or completed outcome
    And no turn is silently lost or replayed

  @owner-client-009
  Scenario: Render a bounded interactive presentation
    Given an assistant response includes a supported presentation
    When the owner opens it in the workspace
    Then the presentation is displayed in an isolated surface
    And it cannot access owner credentials, tools, or task controls

  @owner-client-010
  Scenario: Use the native shell safely
    Given the owner opens the workspace in a supported native shell
    When the owner starts a spoken conversation
    Then microphone permission is requested at that point
    And camera access is not requested
    And navigation remains within the configured private workspace

  # Android and iOS wrappers are scaffolded with neutral IDs. Platform builds
  # and system permission prompt timing are unqualified in this change.

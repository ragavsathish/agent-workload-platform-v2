# Sources: docs/wiki/task-execution-delivery.md and raw/v1/voice-to-durable.feature.
Feature: Complete a selected task and deliver its work product
  One accepted task produces one inspectable outcome through its selected workspace.

  @delivery-001
  Scenario: A scratch task completes through a real worker
    Given an owner has admitted a scratch task
    When its dispatched worker writes a regular file and answers
    Then the task completes once with a bounded answer
    And the owner downloads an archive containing that file

  @delivery-002
  Scenario: A repository task yields only its selected source changes
    Given an owner has admitted a repository task against an approved source snapshot
    When its dispatched worker changes a file in that task's prepared workspace
    Then the task result identifies the same selected source
    And the owner downloads a patch containing that change and no other task's change

  @delivery-003
  Scenario: Host and isolated attempts share the public result contract
    Given two otherwise equivalent admitted tasks select host and isolated execution
    When each worker completes in its selected workspace
    Then each owner view has the same bounded answer and declared work product kind
    And neither view exposes execution credentials or private workspace addresses

  @delivery-004
  Scenario: A missing source or declared image prevents execution
    Given an admitted repository task requires a selected source or declared execution image
    When its prerequisite cannot be verified or prepared
    Then no task environment starts and no completion is advertised
    And the owner can inspect a bounded preparation failure on the same task

  @delivery-005
  Scenario: Worker and capture failures remain attached to the task
    Given an admitted task has been dispatched
    When its worker fails or its work product cannot be captured
    Then the task fails with a bounded reason under its original identity
    And the owner is not offered a download

  @delivery-006
  Scenario: Lost worker state does not replay an effect
    Given a worker started an admitted task and its outcome is uncertain
    When the supervisor restarts and reconciles the task
    Then the task requires an explicit owner retry or remains terminal
    And no second worker attempt starts automatically

  @delivery-007
  Scenario: Late or repeated settlement cannot replace completion
    Given one worker callback completed a task with a captured work product
    When the worker repeats completion or reports a late failure
    Then the recorded answer and artifact declaration are unchanged
    And the owner downloads the original bytes

  @delivery-008
  Scenario: Fresh owner process retrieves the real worker result
    Given a real worker completed a task and captured its work product
    When the owner route starts fresh and the owner reads and downloads the task
    Then the answer and bytes match the worker outcome
    And the download has a private filename and content type

  @delivery-009
  Scenario: Another owner cannot inspect or download the work product
    Given an owner has a completed task from a real worker
    When another owner reads the task or requests its declared artifact
    Then no answer, source identity, or artifact bytes are exposed

# Sources: docs/wiki/task-lifecycle.md and raw/v1/manifest.md.
Feature: Task lifecycle and execution
  An accepted task keeps its identity while work and outcome become visible.

  @lifecycle-001
  Scenario: Accepted work starts within its selected capability
    Given an owner has an accepted task for a selected workspace
    When execution starts
    Then the task becomes inspectably active under its original identity
    And the execution cannot write through an unselected repository grant

  @lifecycle-002
  Scenario: Progress remains bounded and cursor-addressable
    Given an owned task is running
    When the worker reports progress
    Then the owner can read bounded progress after a cursor without awaiting completion
    And later reads return only later progress in order
    And sensitive arguments and private reasoning are absent

  @lifecycle-003
  Scenario: Successful work retains its result
    Given an owned task is running
    When the worker completes successfully
    Then the task is completed with its result under the original identity
    And the owner can still read that result after a fresh start

  @lifecycle-004
  Scenario: Failed work retains its failure
    Given an owned task is running
    When the worker fails
    Then the task is failed with a bounded failure under the original identity
    And it is not reported as completed after a fresh start

  @lifecycle-005
  Scenario: Abort cannot be overwritten by a late result
    Given an owned task is active
    When the owner aborts it and the worker later answers
    Then the task remains terminally cancelled under its original identity
    And no successful result replaces the cancellation

  @lifecycle-006
  Scenario: Uncertain dispatch and interruption retain identity
    Given an accepted task may have been dispatched and its acknowledgement was lost
    When the owner retries the same request after a fresh start
    Then dispatch can recover under the original task identity
    And no second task identity is created

  @lifecycle-007
  Scenario: A parked task can be retried by its owner
    Given a task was interrupted and automatic recovery cannot continue it
    When the owner requests retry
    Then the task resumes under its original identity
    And prior progress remains inspectable

  @lifecycle-008
  Scenario: Another owner cannot inspect or control work
    Given an owner has an active or terminal task
    When another owner reads its progress or result or attempts abort or retry
    Then the other owner receives a bounded missing-task answer
    And no task details or control capability are exposed

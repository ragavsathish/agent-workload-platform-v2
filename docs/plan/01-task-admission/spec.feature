Feature: Task admission
  An authenticated owner can recover one accepted task from one request.

  @admission-001
  Scenario: Eligible request is accepted and visible
    Given an authenticated owner, an eligible workspace, and a bounded instruction
    When the owner submits a new request identity
    Then one task identity is returned
    And the owner can query the accepted task under that identity

  @admission-002
  Scenario: Retry after a lost response reuses the task
    Given an eligible request was accepted but its response was lost
    When the same owner retries the same canonical request identity and input
    Then the original task identity is returned
    And only one task was accepted

  @admission-003
  Scenario: Conflicting reuse is refused
    Given a request identity is bound to accepted input
    When the owner submits different canonical input under that identity
    Then admission is refused as a conflict
    And the accepted task is unchanged

  @admission-004
  Scenario: Ineligible work is refused
    Given an authenticated owner
    When the owner submits an empty instruction or ineligible workspace
    Then admission is refused with a bounded reason
    And no task is accepted

  @admission-005
  Scenario: Owners cannot cross admission or query boundaries
    Given an owner has an accepted task
    When another owner submits as that owner or queries that task
    Then the other owner receives a bounded refusal
    And no task information is exposed

  @admission-006
  Scenario: Simultaneous retries admit one task
    Given an eligible request with one stable identity
    When two submissions of that request overlap
    Then both callers can recover the same task identity
    And only one task was accepted

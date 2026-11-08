Feature: Reconcile uncertain context compaction without duplicate Pi effects

  @session-compaction-uncertain-001
  Scenario: Restart records an unresolved compaction as uncertain and locked
    Given Grain has a pending compaction command C with Pi baseline leaf L and count N
    And the service restarts before observing a Pi outcome
    When the owner reads context
    Then C is exposed as uncertain with a safe recovery reason
    And C still holds the owner session lock

  @session-compaction-uncertain-002
  Scenario: A later Pi branch proof settles an uncertain command
    Given C is uncertain and no Pi effect claim is available
    And the persisted Pi branch has a tail compaction entry parented to baseline L
    When the owner retries C
    Then Grain records completion from the persisted entry
    And Pi compaction is not invoked again

  @session-compaction-uncertain-003
  Scenario: Missing Pi proof preserves the terminal hold
    Given C is uncertain and the Pi branch does not prove completion
    When the owner retries C
    Then C remains uncertain with an owner-readable reason
    And no Pi compaction is invoked
    And turns, selection, and task routing remain blocked

  @session-compaction-uncertain-004
  Scenario: Another owner cannot inspect uncertainty details
    Given owner A has uncertain command C
    When owner B reads context or retries C
    Then owner B receives no command identity, reason, or baseline details

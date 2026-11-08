Feature: Durable session compaction regeneration contract

  @session-compaction-007
  Scenario: Queue compaction requested during an active turn
    Given the owner has selected an eligible conversation and turn T is active
    When the owner requests compaction with command identity C
    Then C is retained as queued for that conversation
    And no Pi compaction effect runs while T is active

  @session-compaction-008
  Scenario: Promote queued compaction after turn settlement
    Given compaction C is queued behind active turn T
    When T settles
    Then C is revalidated against current owner, selection, context, and session state
    And C becomes pending before any Pi compaction effect

  @session-compaction-008
  Scenario: Reject a queued compaction when refreshed context is stale
    Given compaction C is queued behind active turn T
    And the settled turn refreshes context with compaction no longer eligible
    When C is promoted
    Then C is marked rejected with a stale-context reason
    And the pending session lock is released
    And no Pi compaction effect runs

Feature: Durable session context compaction
  The owner can compact one selected Pi conversation with a Grain-tracked
  command that survives uncertain network responses safely.

  @session-compaction-001
  Scenario: Reject compaction before creating pending state
    Given the owner has no selected conversation or context is not eligible
    When the owner requests compaction
    Then Grain refuses the command
    And no pending compaction is recorded

  @session-compaction-002
  Scenario: Record command identity before the Pi effect
    Given the selected Pi branch has a stable leaf and eligible context
    When the owner requests compaction with command identity C
    Then Grain records C as pending with that branch leaf before Pi is called
    And a different command cannot mutate the owner's session while C is pending

  @session-compaction-003
  Scenario: Reconcile a Pi compaction after Grain settlement is lost
    Given command C is pending at Pi branch leaf L
    And Pi has appended one compaction whose parent is L
    When the owner retries C or the service reconciles after restart
    Then Grain records C completed from that Pi entry
    And Pi compaction is not called again

  @session-compaction-004
  Scenario: Keep uncertain compaction fail closed
    Given command C is pending at Pi branch leaf L
    And the current Pi branch does not prove the expected compaction
    When C is retried
    Then C remains pending or uncertain
    And no second Pi compaction is called
    And new turns and selection changes are refused

  @session-compaction-005
  Scenario: Retry the same command identity across a lost HTTP response
    Given the client submitted compaction identity C and did not receive a response
    When the owner retries from the same client instance
    Then the same identity C is sent again
    And a later deliberate compaction after success gets a new identity

  @session-compaction-006
  Scenario: Recover a pending operation after client reload
    Given compaction C is pending in Grain after the client lost its response
    When the owner reloads and queries context
    Then the owner-scoped context reports C and its pending or uncertain status
    And retry resumes C instead of creating a different operation

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
    And a failed revalidation rejects C without running the Pi effect

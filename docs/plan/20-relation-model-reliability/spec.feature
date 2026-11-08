Feature: Reliable bounded relation proposal attempts
  An owner can retry a failed model attempt while review remains the sole publication authority.

  @reliability-001
  Scenario: A failed model attempt has a durable safe outcome
    Given an owned source and two accepted owner concepts
    When a bounded relation attempt fails before returning valid candidates
    Then one failed attempt is recorded with a trace reference and safe failure category
    And no pending or current relation is created
    And another owner cannot read the attempt or its source

  @reliability-002
  Scenario: A valid model candidate reaches pending review
    Given an owned source explicitly states a relation between two accepted concepts
    When a bounded model returns a candidate with exact relation evidence
    Then the complete proposal attempt is recorded as completed
    And exactly one relation enters pending owner review with model and source provenance
    And no current relation is published before owner acceptance

  @reliability-003
  Scenario: A failed attempt can be retried without rewriting history
    Given a bounded attempt failed for an unchanged source and endpoint pair
    When the owner requests a new attempt against the same source and endpoints
    Then the new attempt has a distinct identity
    And the failed outcome remains inspectable and unchanged
    And a valid retry candidate may enter pending review

  @reliability-004
  Scenario: Replaying an attempt identity has one effect
    Given an attempt has already completed or failed
    When that same identity is submitted again
    Then the model is not invoked again
    And the recorded outcome and pending relations are unchanged
    And changed content under the same identity conflicts visibly

  @reliability-005
  Scenario: Invalid or empty output cannot create an edge
    Given a bounded model returns malformed, unsupported, ungrounded, or empty output
    When the result reaches the proposal boundary
    Then the outcome distinguishes invalid output from a valid empty answer and execution failure
    And no pending or current relation is created from that output

  @reliability-006
  Scenario: Recovery and rollback preserve owner decisions
    Given a failed attempt, a later pending retry, and an earlier accepted owner relation
    When the service restarts or the model configuration is rolled back
    Then each attempt and owner decision remains in order and owner scoped
    And only the accepted current relation appears in direct, search, traversal, and export reads
    And no retry duplicates or silently accepts the pending relation

  @reliability-007
  Scenario: Live path qualification requires completed staging
    Given a safe synthetic source and a pinned model, prompt, and relation policy
    When a live bounded attempt runs through model inference and proposal staging
    Then qualification records whether a trace-backed completed attempt and pending relation exist
    And an isolated model candidate or a mocked staging check cannot count as a live path pass

  @reliability-008
  Scenario: A failed live qualification remains open
    Given the live bounded attempt times out, fails, or returns invalid output
    When qualification is reported
    Then its precise outcome and duration are recorded without private source content
    And live staging and semantic quality remain open
    And the previous model configuration remains available for rollback

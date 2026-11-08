Feature: Reconstruct the owner compaction wire and Pi branch proof

  @session-compaction-protocol-001
  Scenario: Recover compaction identity across request retry and page reload
    Given owner context reports command C as queued, pending, or uncertain
    When the owner client retries compaction after reload
    Then it sends C in the x-awp-command-id header to the compact route
    And the owner event context remains the authority for C's status
    And no new command identity is generated for that active operation

  @session-compaction-protocol-002
  Scenario: Reconcile settlement from the persisted Pi branch
    Given the owner event store records baseline leaf L and entry count N for command C
    And the persisted Pi branch has a tail compaction entry E parented to L
    When the runtime retries C after the event-store settlement response was lost
    Then the owner event store records completion from E without invoking Pi compaction again
    And the proof does not claim an atomic snapshot across Pi manager reads

  @session-compaction-protocol-003
  Scenario: Keep clean-room reconstruction limits distinct from current v2
    Given the clean-room fence removes implementation and dependency artifacts
    When the Slice 29 reconstruction report is read
    Then it identifies which facts current v2 tests prove
    And it identifies which facts the fenced reconstruction could not derive

  @session-compaction-protocol-004
  Scenario: Admit compaction from trusted owner projection state
    Given the owner command context identifies the authenticated owner
    And the owner state projection contains the selected eligible conversation
    When a session mutation is proposed
    Then admission reads that projection instead of trusting client eligibility claims
    And concurrent owner mutations are serialized by the owner event tag and compare-and-swap

  @session-compaction-protocol-005
  Scenario: Reject queued compaction after a stale post-turn eligibility refresh
    Given a queued request follows a prior compaction with little remaining context
    When the completed turn refreshes eligibility as false
    Then the owner event store rejects the queued request
    And the Pi branch gains no additional compaction entry

Feature: Private owner review workbench
  Review of evaluation candidates and old metadata is explicit, private, and replayable.

  @workbench-001
  Scenario: Pending inventories open as pending
    Given 120 unreviewed candidate cases and twelve unlinked metadata resources
    When the owner opens both review queues
    Then each queue shows its own pending count and next eligible action
    And no expected label or metadata disposition is preapproved

  @workbench-002
  Scenario: Review resumes without duplicate decisions
    Given an owner saved a draft on a case and an unresolved metadata record
    When the workbench is restarted and the same action is retried
    Then both queues resume at their recorded revisions
    And the repeated action returns its original receipt without a duplicate decision

  @workbench-003
  Scenario: Private source stays private
    Given a candidate containing private source text and a metadata record containing a private link
    When the owner inspects and saves work
    Then only the authenticated local owner can inspect the row
    And shared output contains no source, link, note, or row-level result

  @workbench-004
  Scenario: Expected answers have exact evidence and origin
    Given a pending source and a model suggestion
    When a reviewer labels one supported item and one explicit miss
    Then both expected items retain their origin and exact source anchors
    And the model suggestion remains separate from the reference answer

  @workbench-005
  Scenario: Empty answer and privacy approval are deliberate
    Given a pending source with no supported durable item
    When a reviewer records an explicit empty answer but privacy review is pending
    Then the case remains ineligible for qualification
    And a later explicit privacy decision has its own actor and time

  @workbench-006
  Scenario: Dispute requires independent adjudication
    Given an independent reviewer disputes a case label
    When the same reviewer attempts to adjudicate it
    Then the action is refused
    And a different adjudicator can resolve the dispute without erasing its history

  @workbench-007
  Scenario: Families cannot leak across partitions
    Given two related sources share a semantic family
    When a reviewer assigns them to different partitions
    Then readiness reports the conflict and refuses a qualification run
    And the owner can see the family members and correct the assignment

  @workbench-008
  Scenario: A source-less metadata record stays unresolved
    Given an unlinked metadata resource with a label but no verified source
    When the owner inspects it without making a decision
    Then it remains quarantined with its provenance reason
    And it cannot enter accepted owner knowledge

  @workbench-009
  Scenario: Source linkage requires frozen evidence
    Given an unlinked metadata resource and a proposed source link
    When the frozen snapshot does not prove that link
    Then the link is refused and the resource remains quarantined
    And a verified link can be recorded only with its source identity and evidence

  @workbench-010
  Scenario: Exclusion is explicit and still accounted
    Given a metadata resource shown as non-knowledge material
    When the owner approves exclusion with a reason and evidence
    Then the disposition retains the actor, snapshot, reason, evidence, and decision history
    And the resource remains in snapshot accounting and rollback material

  @workbench-011
  Scenario: Stale and foreign actions cannot change the queues
    Given an owner case and an owner metadata record at known revisions
    When a second owner reads or decides, or the first owner submits a stale revision
    Then the foreign request is denied and the stale decision is refused
    And current projections remain unchanged

  @workbench-012
  Scenario: Replay preserves both review histories
    Given reviewed, disputed, excluded, and unresolved decisions
    When decision history is replayed after restart
    Then both owner-scoped queues and their revisions match their prior views
    And no evaluation label becomes current owner knowledge

  @workbench-013
  Scenario: Quality, shadow, and live choice remain separate
    Given some reviewed cases and some unresolved metadata resources
    When the owner requests progress and private handoff reports
    Then each report states its own missing work and exact private rows
    And no locked holdout is consumed or live read source changed

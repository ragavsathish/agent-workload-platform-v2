Feature: Reviewed quality for owner knowledge
  Model proposals need independent labels and a frozen, private qualification gate.

  @quality-001
  Scenario: Draft owner material is not reviewed gold
    Given pending private annotation drafts and a checked-in regression fixture
    When readiness is assessed
    Then neither source qualifies as independently reviewed reference answers
    And the quality gate stays open without exposing a case

  @quality-002
  Scenario: Independent review and an explicit miss preserve provenance
    Given a source with a model suggestion and an owner correction
    When an independent reviewer approves a label and records a missed item
    Then both reference answers retain exact source anchors and ordered review history
    And neither reference answer is published merely by entering the corpus

  @quality-003
  Scenario: A leaking or unapproved split is refused
    Given two near-duplicate sources in one semantic family and a pending privacy review
    When the sources are assigned to different partitions
    Then readiness fails for family overlap and pending privacy review
    And no locked-holdout evaluation begins

  @quality-004
  Scenario: Each model branch receives its own score
    Given reviewed journal, assertion, and subject-relation cases
    When a frozen candidate is evaluated through the live proposal boundaries
    Then each branch has its own denominator, confusion, omissions, additions, and runtime result
    And success in one branch cannot hide errors in another

  @quality-005
  Scenario: An exact excerpt can still carry a false meaning
    Given a reviewed question about a skill that supports no owner skill claim
    When the model repeats an exact excerpt and proposes skill evidence
    Then the excerpt check passes and the unsupported addition is counted
    And the proposal is not accepted or published by the evaluator

  @quality-006
  Scenario: Private disagreements remain private
    Given a reviewed case whose model output disagrees with the reference answer
    When the report is written
    Then its row-level source, prediction, and reviewer note are in a restricted private result
    And the public summary contains only disclosure-reviewed aggregates

  @quality-007
  Scenario: Thresholds and a single holdout use are frozen before scoring
    Given a candidate, baseline, policy, and locked holdout with recorded identities
    When the frozen candidate is scored once
    Then every required metric and safety gate determines pass or fail
    And a second tuning run against that holdout version is refused

  @quality-008
  Scenario: Restart cannot turn a proposal into owner knowledge
    Given a reviewed label and a pending or rejected model proposal
    When the histories and graph reads are rebuilt after restart
    Then the label remains an evaluation reference only
    And the pending or rejected proposal remains absent from current owner reads
    And another owner cannot inspect either private history

  @quality-009
  Scenario: Missing review and migration remain separate open gates
    Given a passing protocol check but incomplete independent review
    When a qualification status is requested
    Then model quality is open with the missing coverage reported
    And private migration and public deployment retain their own verdicts
    And the prior model configuration remains the rollback target

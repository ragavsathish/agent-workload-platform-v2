Feature: Owner knowledge taxonomy and reviewed model proposals
  Owner facts have explicit categories; model suggestions remain proposals until reviewed.

  @taxonomy-001
  Scenario: Existing journal and assertion kinds map to a versioned hierarchy
    Given the first owner knowledge category version
    When the category tree is read
    Then the seven journal kinds and three assertion kinds have stable identities and defined parents
    And no assertion is an outcome rather than a publishable item kind
    And a topic is not presented as proof of skill

  @taxonomy-002
  Scenario: An old item retains its original meaning across a category revision
    Given a reviewed item records an earlier category version
    When a later category version supersedes its category
    Then the original classification and source remain inspectable
    And a current mapping requires an explicit versioned decision

  @taxonomy-003
  Scenario: An unsupported category cannot enter current owner knowledge
    Given a result names a category outside the active hierarchy
    When it is incorporated
    Then its category is refused with a visible reason
    And no accepted fact or graph instance appears

  @taxonomy-004
  Scenario: An exact quote model suggestion waits for owner review
    Given a settled owner source and a bounded model proposal with an exact excerpt
    When the proposal is recorded
    Then its source, model, prompt, category version, and result identity are retained
    And its item is pending rather than accepted
    When the owner accepts it at the current revision
    Then the item appears in current owner knowledge once

  @taxonomy-005
  Scenario: A plausible label with unsupported meaning is refused
    Given a model proposes a skill for a source that only asks a question about the skill
    When the proposal is validated
    Then quote containment alone does not publish or accept the skill
    And the rejected interpretation remains inspectable

  @taxonomy-006
  Scenario: A proposed subject relation needs a separate decision
    Given a model suggests one owner subject is broader than another
    When both endpoint items are pending or one belongs to another owner
    Then the relation cannot appear in current owner reads
    When the owner accepts both endpoints and the exact relation
    Then the relation appears once with its source and review decision

  @taxonomy-007
  Scenario: Replay, conflict, timeout, and empty results are distinct
    Given one proposal result has been incorporated
    When the same result arrives again
    Then no duplicate item or relation is made
    When changed content arrives under that result identity
    Then the conflict is refused and visible
    When another model attempt times out or returns no grounded proposal
    Then that outcome is visible and publishes nothing

  @taxonomy-008
  Scenario: Rebuild and revocation preserve current authority
    Given an owner accepted and later revoked a proposed item and its relation
    When category and graph reads are rebuilt
    Then current reads exclude the item and relation
    And source, proposal, and ordered review actions remain inspectable

  @taxonomy-009
  Scenario: Two owners remain isolated in every graph read
    Given two owners have similarly labeled concepts and relations
    When either owner searches, traverses, exports, or directly reads a concept through the owner API
    Then no item, source text, or edge of the other owner is disclosed

  @taxonomy-010
  Scenario: Model quality is measured on a reviewed corpus
    Given a reviewed corpus and precommitted thresholds with pinned model, prompt, and taxonomy versions
    When the model path is evaluated
    Then the report counts false additions, misses, excerpt errors, category errors, and wrong relations
    And qualification passes only when every threshold is met

  @taxonomy-011
  Scenario: Protocol success does not waive the quality gate
    Given deterministic proposal and graph checks pass
    When no reviewed corpus or precommitted thresholds are available
    Then model quality remains open
    And live owner cutover is not declared complete

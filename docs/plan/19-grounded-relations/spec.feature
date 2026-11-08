Feature: Grounded owner relation proposals
  A model may suggest subject relations, and only the owner may publish them.

  @relation-001
  Scenario: A bounded model suggests a hierarchy edge with evidence
    Given an owner source explicitly says one accepted subject is a kind of another accepted subject
    When a bounded relation proposal attempt runs
    Then it returns at most the declared candidate limit
    And each candidate identifies the owner, source, both endpoints, direction, kind, exact relation excerpt, model, prompt, policy version, and trace
    And no current owner edge is published

  @relation-002
  Scenario: An excerpt naming two subjects does not prove their relation
    Given a source merely lists two accepted subjects without relating them
    When a model suggests a broader or related edge using that exact list excerpt
    Then exact containment alone cannot accept or publish the edge
    And a reviewed unsupported interpretation is counted as a wrong edge

  @relation-003
  Scenario: Unsupported factual and causal predicates are refused
    Given a source states a personal or causal fact between subjects
    When a model proposes a factual or causal predicate
    Then the candidate is refused as an unsupported kind
    When the model instead mislabels that fact as a subject association with an exact excerpt
    Then it remains pending until the owner rejects it
    And the reviewed false association counts as a wrong edge
    And no subject edge appears in current owner reads

  @relation-004
  Scenario: Empty, invalid, timeout, and failed attempts are visible
    Given an owned source and accepted endpoint pair
    When the bounded model returns no relation, malformed output, invalid excerpt, or times out
    Then each outcome is distinguishable in the attempt history
    And none creates a pending or accepted edge from invalid output

  @relation-005
  Scenario: A valid candidate enters pending review once
    Given a bounded candidate with exact relation evidence and accepted owner endpoints
    When the candidate is staged twice with the same result identity
    Then one pending relation exists with its immutable provenance
    And neither attempt publishes it
    When changed content uses that result identity
    Then the changed result conflicts visibly

  @relation-006
  Scenario: The owner accepts a relation at the current revision
    Given a pending relation with two accepted owner endpoints and exact edge evidence
    When the owner accepts it at its current revision
    Then one current edge appears in owner search, traversal, and export with its review action
    And a stale revision or a second owner cannot accept it

  @relation-007
  Scenario: A corrected direction is a linked replacement
    Given a pending broader proposal points in the wrong direction
    When the owner corrects its direction and evidence
    Then the original remains inspectable as rejected with a correction link
    And a new linked proposal is pending
    When the owner separately accepts the replacement
    Then only the corrected direction is current

  @relation-008
  Scenario: Reject and revoke remove current visibility
    Given one pending relation and another accepted relation
    When the owner rejects the pending one and revokes the accepted one
    Then neither appears in current owner reads
    And ordered proposal and review history remains inspectable

  @relation-009
  Scenario: Invalid endpoints and cycles never publish
    Given a candidate refers to a pending, revoked, missing, foreign, or identical endpoint
    When staging or acceptance is attempted
    Then the relation is refused and no edge appears
    Given an accepted broader path would be closed into a cycle
    When its final edge is accepted
    Then the cycle is refused without changing current graph reads

  @relation-010
  Scenario: Restart, repair, and owner isolation preserve authority
    Given two owners have similarly labeled concepts and one owner later revokes an accepted relation or endpoint
    When the graph is rebuilt after restart
    Then each owner's current graph equals its pre-restart authoritative review view
    And the revoked edge is absent from direct read, search, traversal, and export
    And no source or edge of the other owner is disclosed

  @relation-011
  Scenario: Reviewed relation cases score the actual model path
    Given independently reviewed positive, negative, ambiguous, and corrected relation cases with frozen policy
    When the actual bounded relation proposal path is evaluated
    Then wrong, missing, reversed, mistyped, unsupported, and invalid-evidence edges are counted separately
    And the relation branch has its own denominator, model provenance, and runtime result
    And evaluation labels do not publish owner knowledge

  @relation-012
  Scenario: Structural success leaves unreviewed quality open
    Given all deterministic relation scenarios pass but no eligible independent relation corpus exists
    When relation qualification is reported
    Then its semantic quality outcome is open
    And no broad model-quality pass or live owner cutover is claimed

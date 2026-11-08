# Sources: docs/wiki/public-v2-candidate.md and raw/v1/public-candidate-host-observation.md.
Feature: Isolated public v2 candidate
  A separate candidate proves owner access and spoken continuity without changing the running owner service.

  @candidate-001
  Scenario: Candidate preflight inventories every occupied resource
    Given the existing owner service is healthy
    When a candidate with an occupied address or existing state identity is proposed
    Then preflight refuses to start it
    And the existing service remains healthy with the same resource identities

  @candidate-002
  Scenario: Candidate starts with independent identity
    Given candidate addresses, state, and owner entry are distinct and available
    When the candidate starts and restarts
    Then its state and owner entry retain one candidate identity
    And no existing service resource is adopted or replaced

  @candidate-003
  Scenario: Unsafe or missing entry leaves candidate unexposed
    Given a candidate has no verified current entry or authenticated origin
    When public readiness is requested
    Then exposure is refused with a named reason
    And local readiness cannot be reported as public readiness

  @candidate-004
  Scenario: Exact owner enters candidate from a separate device
    Given the candidate has an authenticated private origin and exact owner
    When the owner reaches it from a distinct authorized device
    Then the entry grants selected history and signaling for that owner
    And the actual origin and peer identity are recorded

  @candidate-005
  Scenario: Spoofed and identity-less peers are refused
    Given the exact owner can reach a healthy candidate
    When a foreign or identity-less peer supplies owner headers, local hints, or a service credential
    Then owner reads and signaling are refused
    And no owner turn or task capability is exposed

  @candidate-006
  Scenario: Candidate carries one complete spoken journey
    Given the exact owner opened the candidate from a distinct device
    When the owner sends a timed final utterance
    Then a reachable media path carries input and non-silent reply audio
    And one settled turn appears in the selected conversation
    And the public turn matches the candidate's private authority

  @candidate-007
  Scenario: Failed media does not count as spoken success
    Given owner signaling succeeds but no usable media path is selected
    When the spoken journey reaches its deadline
    Then the journey has a bounded failure or inspectable uncertainty
    And no completed spoken turn is claimed

  @candidate-008
  Scenario: Candidate restart retains owner history
    Given the candidate completed a public spoken turn and accepted a task
    When its services restart without changing the candidate state identity
    Then the same owner can read the same selected turn and task
    And neither the settled turn nor accepted task is duplicated

  @candidate-009
  Scenario: Failed change restores last qualified candidate
    Given a qualified candidate has a selected conversation and accepted task
    When a replacement fails readiness or the owner journey
    Then the last qualified version is restored at the same candidate origin
    And the owner can read the same turn and task after rollback

  @candidate-010
  Scenario: Foreign denial and owner success are paired
    Given a foreign peer is denied at the candidate entry
    When the candidate's release evidence is evaluated
    Then denial counts only alongside a passing exact-owner journey on that healthy candidate
    And an outage cannot count as owner isolation

  @candidate-011
  Scenario: Missing external prerequisites remain open
    Given the exact owner, authenticated candidate node, or distinct peer is absent
    When the release decision is prepared
    Then the missing public gates are named and remain unqualified
    And the existing owner service stays available

# Sources: docs/wiki/public-deployment.md, docs/wiki/deployed-voice-journey.md, and raw/v1/host-requirements.md.
Feature: Public owner deployment and service lifecycle
  A private owner journey works through independently running services and survives change.

  @deploy-001
  Scenario: Exact owner enters through the public route
    Given the private public entry is ready for one configured owner
    When that owner opens the voice route from a separate authorized device
    Then the entry resolves that owner before signaling or selected-history reads
    And the owner can open the selected conversation

  @deploy-002
  Scenario: Foreign and spoofed callers cannot gain owner authority
    Given the public route contains an owned conversation
    When a foreign caller sends owner identity, local-access markers, or an inner service credential
    Then signaling and selected-history reads are refused
    And no owner turn or task capability is exposed

  @deploy-003
  Scenario: Live media crosses the external route
    Given the owner has opened a live connection from a separate device
    When the owner sends timed audio for one final utterance
    Then a reachable media path carries input and non-silent reply audio
    And one accepted turn reaches the selected conversation
    And the safe public answer agrees with the settled turn

  @deploy-004
  Scenario: Signaling without media is not a spoken success
    Given an authorized owner can complete signaling
    When no usable media path is established
    Then the connection reports a bounded failure or inspectable uncertainty
    And no completed spoken turn is claimed

  @deploy-005
  Scenario: Service restart preserves selected history and identity
    Given the owner completed a public spoken turn
    When the conversation and speech services restart under supervision
    Then the owner can resume the same selected conversation through the public route
    And its safe turn history retains the same turn identity
    And the completed utterance is not dispatched again

  @deploy-006
  Scenario: Independent executor schedules accepted work
    Given an owner task is accepted while the executor is running
    When capacity becomes available without a manual per-task launch
    Then the task enters one owned execution attempt
    And the owner can inspect its safe progress or outcome

  @deploy-007
  Scenario: Executor interruption recovers eligible work
    Given an accepted task is pending or executing
    When the executor stops and starts again
    Then the original task identity remains visible
    And eligible work resumes or has an inspectable recovery reason
    And no claimed attempt runs twice

  @deploy-008
  Scenario: Failed service change rolls back without losing work
    Given a qualified service version has an owned conversation and accepted task
    When a new version fails readiness or the public owner journey
    Then the last qualified version is restored
    And the same public owner route can read the conversation and task
    And neither a settled turn nor task acceptance is duplicated

  @deploy-009
  Scenario: Dependency failure blocks public readiness
    Given a required speech, conversation, or task authority is unavailable
    When service readiness is checked
    Then the affected capability is reported unavailable
    And the service does not claim a completed owner journey

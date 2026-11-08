Feature: Managed operational incident investigations
  Approved firing alerts trigger bounded, attributable, read-only work.

  @incident-001
  Scenario: An approved firing alert starts one investigation
    Given an approved firing alert with a fingerprint
    When a successful alert-list observation is processed
    Then one incident epoch and one stable investigation request are recorded
    And one task is accepted under that request identity

  @incident-002
  Scenario: Unapproved and non-firing observations cannot start work
    Given observations with an unknown rule, wrong manager, disabled investigation, ambiguous name, or non-firing state
    When the observations are processed
    Then none creates an incident epoch or investigation task

  @incident-003
  Scenario: Repeated and duplicate observations retain one epoch
    Given a firing incident epoch has an accepted investigation
    When a poll repeats or duplicates that same alert
    Then its first-seen time, request identity, and task identity remain the same
    And no second investigation is accepted

  @incident-004
  Scenario: Resolution and cooldown govern a new epoch
    Given an investigated alert is absent from a successful observation
    When the same alert returns before the cooldown ends
    Then the previous epoch and investigation remain
    When it resolves and returns after the cooldown ends
    Then a new epoch with a new request identity may start

  @incident-005
  Scenario: An unavailable alert service does not resolve other incidents
    Given an active managed incident
    When the alert list is unavailable
    Then the active incident remains active with stale observation freshness
    And no absence-based resolution is recorded

  @incident-006
  Scenario: Three consecutive failures open one alert-service outage
    Given the alert service has failed twice in succession
    When it fails a third time and continues failing
    Then one outage epoch has one investigation request
    And other active incidents are retained
    When a successful observation arrives
    Then the failure streak resets and the outage can resolve

  @incident-007
  Scenario: Bounded evidence cannot turn alert payload into instructions
    Given an approved rule, malicious alert labels, and metric samples including private or invalid fields
    When investigation evidence is captured
    Then the instruction uses only approved rule meaning and bounded safe facts
    And unavailable metrics are marked unavailable
    And the instruction forbids remediation and unsupported claims

  @incident-008
  Scenario: Uncertain submission retries the same request
    Given an incident intent is recorded and task acknowledgement is lost
    When the service restarts and retries submission
    Then the canonical instruction and request identity are unchanged
    And the task authority accepts at most one task

  @incident-009
  Scenario: Owner reads the incident and investigation outcome
    Given an owner's incident has an accepted investigation task
    When the task completes, fails, or remains unjudged
    Then the owner sees the incident, evidence status, task identity, and corresponding outcome
    And a failed or unjudged investigation is not shown as a finding
    And no product change is inferred from it

  @incident-010
  Scenario: Incident reads are isolated and rebuildable
    Given two owners have incident histories and one owner has resolved an alert
    When either owner lists or directly reads incidents after a read-model rebuild
    Then only that owner's epochs and task outcomes are visible
    And the resolved history remains inspectable without resubmitting work

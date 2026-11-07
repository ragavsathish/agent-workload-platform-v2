# Sources: docs/wiki/personal-knowledge.md and raw/v1/c3-personal-knowledge.feature.
Feature: Personal knowledge from current owner conversations
  Completed owner turns produce attributable, reviewable knowledge and explicit daily accounts.

  @knowledge-001
  Scenario: Settled owner turn starts independent extraction once
    Given an authenticated owner has one settled turn with a stable source identity
    When the completed turn is observed repeatedly
    Then one journal task and one classification task belong to that source
    And the conversation answer remains settled

  @knowledge-002
  Scenario: Incomplete or non-owner turn does not enter knowledge intake
    Given a pending, interrupted, empty, or non-owner message
    When intake examines the conversation
    Then no knowledge task is accepted for that message

  @knowledge-003
  Scenario: One extraction failure does not erase its sibling
    Given both extraction tasks were accepted for one source turn
    When journal extraction fails and classification succeeds
    Then both distinct outcomes remain inspectable
    And the classification suggestions remain reviewable with their source

  @knowledge-004
  Scenario: Owner correction retains source and review history
    Given a source-backed journal entry is pending review
    When its owner corrects it against the current revision
    Then the current interpretation shows the correction
    And the original suggestion and review action remain attributable

  @knowledge-005
  Scenario: Assertion requires owner acceptance for profile use
    Given a source-backed candidate assertion is pending review
    When its owner accepts or rejects it
    Then an accepted candidate may appear as a profile fact
    And a rejected or unreviewed candidate does not appear as an accepted profile fact

  @knowledge-006
  Scenario: Stale and repeated review commands are distinct
    Given an owner review has changed an item's revision
    When the same decision is repeated or a competing stale decision arrives
    Then the repeated decision resolves to the recorded action
    And the competing stale decision is refused without changing current interpretation

  @knowledge-007
  Scenario: Foreign caller cannot read or review personal knowledge
    Given one owner has a source-backed suggestion and review history
    When a foreign caller presents the item identity or an inner writer credential
    Then the caller cannot read the suggestion or history
    And no review action is recorded

  @knowledge-008
  Scenario: Explicit daily source bundle produces one attributed version
    Given an owner supplies a bounded source bundle for a local date and time zone
    When the same daily build request is submitted twice
    Then one task and account version identify that exact bundle
    And no completed turn starts a daily build by itself

  @knowledge-009
  Scenario: Changed daily sources do not replace prior attribution
    Given an account version exists for one source bundle
    When its owner explicitly requests the same date with a changed bundle
    Then a distinct version identifies the changed bundle
    And the prior version remains attributable to its original sources

  @knowledge-010
  Scenario: Invalid daily bundle is refused before work
    Given a source bundle is empty, oversized, duplicated, or outside the requested local date
    When the owner requests a daily account
    Then no daily build task is accepted

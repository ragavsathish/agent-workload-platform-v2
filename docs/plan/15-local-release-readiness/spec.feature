# Sources: docs/wiki/local-release-readiness.md and raw/v1/expanded-storming.md.
Feature: Qualify a supervised local owner release
  One selected version earns only the readiness claims its owner journeys prove.

  @release-001
  Scenario: Fresh supervision reports the actual required services
    Given a selected release and a fresh private state identity
    When its services start under supervision
    Then each required service has an observed ready or unavailable result
    And the release report identifies its version and actual state identity

  @release-002
  Scenario: One spoken turn reaches a selected conversation
    Given the owner selected a saved conversation in the supervised release
    When the owner sends a final utterance through a live local media connection
    Then one accepted turn reaches that selected conversation
    And the owner receives non-silent response audio and the matching safe answer

  @release-003
  Scenario: Delegated work completes and its product reaches the owner
    Given the owner admitted work for one selected workspace
    When the independent executor accepts and completes one attempt
    Then the same task has a bounded answer and a captured work product
    And the owner downloads the exact declared bytes through a fresh owner route

  @release-004
  Scenario: Reviewed knowledge and hierarchy survive replay
    Given a completed owner source yields a grounded knowledge proposal
    When the owner reviews its category and a relation to an accepted concept
    Then pending and rejected material stays out of current owner reads
    And accepted material and its provenance survive a fresh read process

  @release-005
  Scenario: Approved lyrics produce private owner audio
    Given the owner received a complete lyric manifest
    When the owner approves its exact version and a separate render completes
    Then the owner can play the resulting non-silent audio
    And no audio is offered for an unapproved or failed render

  @release-006
  Scenario: A managed alert yields one inspectable investigation
    Given a selected managed alert is firing in the supervised release
    When the alert observation starts its bounded investigation
    Then one stable incident and task identity are recorded
    And the owner can inspect the bounded finding, failure, or unjudged outcome

  @release-007
  Scenario: Restart preserves state without replaying effects
    Given a spoken turn, a completed task, and reviewed knowledge were recorded
    When the conversation and executor services restart with the same private state
    Then the owner reads the same turn, task, product declaration, and review state
    And neither a settled turn nor a claimed worker attempt is run again

  @release-008
  Scenario: An uncertain worker attempt requires explicit recovery
    Given a task attempt was claimed and its effect is uncertain
    When the executor restarts
    Then the original task has an inspectable recovery reason
    And no second attempt starts until the owner explicitly retries

  @release-009
  Scenario: Another caller cannot cross owner boundaries
    Given the release contains an owner's conversation, task, knowledge, song, and incident
    When another caller reads or acts through the owner routes
    Then no selected history, task answer or bytes, knowledge, audio, or incident is disclosed
    And no owner action or task is created for that caller

  @release-010
  Scenario: Missing dependency is reported without a false pass
    Given a required service, model, or external source is unavailable
    When release readiness and its owner journey are checked
    Then the affected journey is failed, unavailable, or unqualified with a reason
    And the release report does not promote a fixture or status response to owner success

  @release-011
  Scenario: Status distinguishes local proof from external qualification
    Given the local owner journeys have an evidence report
    When release status and documentation are published
    Then each current journey cites its exercised result or open gate
    And separate-device access, external image build, private migration, model quality, and pull request publication remain open without their own evidence

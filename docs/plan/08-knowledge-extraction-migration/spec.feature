Feature: Source-backed knowledge extraction and migration
  A completed owner turn can produce independently reviewable knowledge, and prior knowledge survives a verified move.

  @extraction-01
  Scenario: Independent processing outcomes
    Given a settled owner turn has journal and classification tasks
    When journal processing fails and classification returns a candidate
    Then the conversation answer remains settled
    And the journal failure is inspectable and retryable under its task identity
    And the classification candidate retains its source and evidence

  @extraction-02
  Scenario: Empty outcomes remain visible
    Given an owner turn contains only a routine command
    When journal and classification processing finish without suggestions
    Then each mode records its own empty outcome
    And no accepted profile fact appears

  @extraction-03
  Scenario: Replay and conflict
    Given a validated result has been incorporated for a source task
    When the same result arrives again
    Then no duplicate suggestion or review action is made
    When different content arrives under that result identity
    Then the conflicting result is refused and visible for investigation

  @extraction-04
  Scenario: Reject unsupported output
    Given a processor returns a suggestion without an exact owner source excerpt
    When the result is incorporated
    Then the unsupported suggestion is refused
    And the source task records the validation outcome

  @extraction-05
  Scenario: Daily account matches its request
    Given a daily account task names one bounded source bundle
    When a result names a different source bundle
    Then the result is refused
    And the original task remains recoverable

  @projection-01
  Scenario: Review history is retained through repair
    Given an owner corrected a journal entry and later accepted a candidate
    When the knowledge read view is rebuilt after failure
    Then the original suggestions, ordered actions, current interpretation, and source references agree
    And only the accepted candidate appears among accepted facts

  @projection-02
  Scenario: Owner isolation
    Given two owners have suggestions with similar source references
    When one owner reads or reviews knowledge
    Then the other owner's source text and actions are not disclosed or changed

  @migration-01
  Scenario: Import replays safely
    Given a frozen legacy snapshot and its manifest
    When import stops after a checkpoint and resumes twice
    Then every mapped resource and review action appears once
    And the original legacy identity remains traceable

  @migration-02
  Scenario: Uncertain provenance is quarantined
    Given a legacy claim has no provable owner or source
    When the snapshot is imported
    Then the claim is quarantined with its reason
    And it does not become an accepted owner fact

  @migration-03
  Scenario: Discrepancy blocks cutover
    Given an imported snapshot has a missing review action or daily version
    When reconciliation runs
    Then the discrepancy is reported by resource class
    And owner-visible reads remain on the prior source

  @migration-04
  Scenario: Rollback restores the prior reads
    Given a reconciled import was selected for owner reads
    When the rollback rehearsal selects the prior snapshot
    Then owner-visible knowledge agrees with the prior snapshot
    And neither source history nor imported history is erased

  @qualification-01
  Scenario: Live extraction quality is measured
    Given a reviewed corpus and pinned processing versions
    When live extraction is evaluated
    Then false additions, missed statements, excerpt accuracy, and category errors are reported by mode
    And the quality gate remains open until reviewed thresholds are met

  @qualification-02
  Scenario: Old data gate is separate
    Given the live extraction quality gate passes
    When no authorized real legacy snapshot has reconciled
    Then the old data migration gate remains open
    And live knowledge cutover is not declared complete

# Sources: docs/wiki/pr-soundtrack.md and raw/v1/song-pr-workflow.md.
Feature: Qualify a manually published pull request soundtrack
  The maintainer and owner preserve a reviewable engineering change and song.

  @soundtrack-001
  Scenario: Ground a lyric brief in the selected change
    Given an AWP self-evolution pull request has a reviewed revision and engineering summary
    When the maintainer prepares its soundtrack brief
    Then the brief reflects permitted facts from that reviewed change
    And the pull request remains understandable without listening to music

  @soundtrack-002
  Scenario: Review the complete lyric version before render
    Given a song generation has a complete lyric manifest for the reviewed change
    When the owner reviews and approves its full title, style, and lyrics
    Then the approved lyric version is identifiable
    And a changed version requires another review before rendering

  @soundtrack-003
  Scenario: A style alternate is a separate reviewed generation
    Given the owner approved one lyric version
    When the maintainer requests a different musical arrangement
    Then the alternate uses a separate generation
    And its complete lyric lines are compared with the approved version

  @soundtrack-004
  Scenario: Inspect rendered audio before publication
    Given the owner approved the exact lyric version
    When rendering produces a private audio result
    Then the maintainer inspects format and duration and listens to the complete result
    And a failed or unplayable result is not represented as published audio

  @soundtrack-005
  Scenario: Publish a stable reviewer-accessible asset
    Given a reviewed compact audio result is ready
    When the maintainer commits it for the pull request
    Then documentation uses repository-relative links to the committed asset
    And the pull request uses a commit-pinned link accessible to the intended reviewer

  @soundtrack-006
  Scenario: Preserve the engineering review in the final pull request
    Given the asset and its approved full lyrics are available
    When the maintainer updates the pull request
    Then its body includes complete lyrics and the reviewer-accessible song link
    And its technical outcome, architecture decisions, and verified checks remain readable

  @soundtrack-007
  Scenario: Missing evidence remains unverified
    Given the song fails, its lyric version changes, or the reviewer link is inaccessible
    When the maintainer inspects soundtrack completion
    Then the missing or failed stage is reported
    And no soundtrack attachment or release exception is inferred from that absence

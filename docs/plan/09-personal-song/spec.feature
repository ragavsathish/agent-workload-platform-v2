Feature: Create and review a personal song
  The owner reviews exact lyrics before a separate render produces private audio.

  @song-001
  Scenario: Request lyrics once despite replay
    Given an owner has an eligible workspace and a valid song brief
    When the owner repeats one creation request with the same content
    Then one generation and one lyric task exist
    And both responses refer to the same generation

  @song-002
  Scenario: A conflicting request identity is refused
    Given an owner has requested a song
    When the owner reuses its creation request identity with changed content
    Then the request is refused as a conflict
    And the original generation is unchanged

  @song-003
  Scenario: Review a complete lyric manifest
    Given lyric work completes with a valid song manifest
    When the owner inspects the generation
    Then the full title, style, lyrics, duration, tempo, key, and meter are presented
    And the generation is lyrics ready with no audio

  @song-004
  Scenario: Render requires approval of the exact manifest
    Given a generation has lyrics ready for review
    When render is requested without recorded owner approval
    Then no render task is created
    When the owner approves the presented manifest digest and requests render
    Then one render task receives that manifest

  @song-005
  Scenario: Rejection or changed lyrics require a new review
    Given a generation has lyrics ready for review
    When the owner rejects its manifest
    Then rendering that generation is refused
    And a changed manifest requires a new generation and approval

  @song-006
  Scenario: Render replay starts no second task
    Given the owner approved a manifest and requested rendering
    When the render request is repeated after an uncertain response
    Then the same render task is returned
    And no second render effect starts

  @song-007
  Scenario: A completed render supplies private audio
    Given approved render work completes with a stored audio artifact
    When the owner inspects and plays the generation
    Then it is complete and its audio is available through an owner-checked path
    And internal renderer and storage paths are absent from the public view

  @song-008
  Scenario: Failure and cancellation have bounded endings
    Given lyric work fails or completes with an invalid manifest
    Then rendering is refused and a bounded failure is visible
    Given render work fails or completes without audio
    Then no playable audio is exposed and replay does not restart render
    Given the owner cancels a generation while work is running
    Then it remains cancelled even if work completes later

  @song-009
  Scenario: Song ownership is enforced at every boundary
    Given one owner has a generation with approved lyrics and completed audio
    When another owner inspects, approves, renders, cancels, or plays that generation
    Then each operation reveals no generation
    And no new task or approval is recorded

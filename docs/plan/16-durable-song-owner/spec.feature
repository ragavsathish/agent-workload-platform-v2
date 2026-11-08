# Sources: docs/wiki/durable-song-journey.md, docs/wiki/personal-song.md, raw/v1/song-workspace-effect.py.
Feature: Complete a durable owner song journey
  A real claimed lyric task yields the exact manifest that the owner later approves for private audio.

  @durable-song-001
  Scenario: A fresh request starts one actual lyric task
    Given an owner has an eligible workspace and a bounded song brief
    When the owner creates a song with one request identity
    Then one generation and one stable lyric task are accepted
    And a worker claims that task with the selected workspace and brief
    And no manifest or audio is offered before the task result is validated

  @durable-song-002
  Scenario: A claimed lyric task produces a reviewable manifest
    Given the owner's lyric task was claimed and completed by its worker
    When the owner checks the generation
    Then its full structured manifest comes from that task result
    And the title, style, sectioned lyrics, duration, tempo, key, meter, and digest are shown
    And the generation is lyrics ready without audio

  @durable-song-003
  Scenario: Later exact approval leads to private playable audio
    Given the owner has inspected a complete lyric manifest
    When the owner later approves that exact digest and requests rendering
    Then one separate render receives the approved manifest
    And the owner can play the completed non-silent audio through an owner-checked path
    And the journey evidence links the request, lyric task, decision, render, and audio

  @durable-song-004
  Scenario: No approval or a changed manifest cannot start a render
    Given valid lyrics are ready for owner review
    When rendering is requested before explicit approval or with another digest
    Then no render task or audio is created
    When the owner rejects the exact manifest
    Then a later render of that generation is refused

  @durable-song-005
  Scenario: Request and render replay preserve one effect each
    Given the owner has created and later approved a generation
    When creation, lyric submission, and render requests are repeated after uncertain responses
    Then the original generation, lyric task, and render task are returned
    And each worker effect is started at most once without an explicit recovery decision

  @durable-song-006
  Scenario: Invalid or missing lyric result cannot be presented as ready
    Given a claimed lyric task completes without a valid structured manifest
    When the owner checks the generation
    Then a bounded lyric failure is visible and no approval or render is accepted
    Given the lyric owner is unavailable
    Then the generation remains pending or is reported unavailable with a reason
    And no supplied manifest qualifies the journey

  @durable-song-007
  Scenario: Restart and cancellation do not publish late work
    Given lyric or render work is active for an owner generation
    When its services restart with the same state or the owner cancels the generation
    Then the original task and decision identities remain inspectable
    And uncertain work is not silently repeated
    And a late completion after cancellation cannot expose playable audio

  @durable-song-008
  Scenario: A foreign caller cannot inspect or advance the song
    Given one owner has a lyric task, reviewed manifest, and private audio
    When another caller reads, approves, renders, cancels, or plays that generation
    Then each operation reveals no generation or audio
    And no task, decision, or render effect is caused by that caller

  @durable-song-009
  Scenario: The local release claim follows full journey evidence
    Given a fresh supervised release has completed the owner song journey
    When its evidence is checked against task, decision, render, and owner playback records
    Then the local song journey is exercised with attributable identities and digests
    And a report missing actual lyric execution stays unqualified

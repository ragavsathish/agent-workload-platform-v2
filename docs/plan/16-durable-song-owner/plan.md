# Slice 16: durable lyric owner and complete song journey

Cites the [durable song journey](../../wiki/durable-song-journey.md), [personal song rules](../../wiki/personal-song.md), [local release rules](../../wiki/local-release-readiness.md), [slice 09 plan](../09-personal-song/plan.md), and [slice 15 plan](../15-local-release-readiness/plan.md).

## Goal and boundary

Close the missing lyric-owner link in the personal song journey. From a fresh owner request, prove an actual claimed lyric task produces the exact reviewable manifest, then a later owner decision permits a separate render and private playback. The [scenarios](spec.feature) define the full chain and its refusal, replay, restart, failure, and isolation branches.

This slice joins existing song decisions and local release qualification. It begins at owner request and ends at private audio or a bounded terminal or unavailable result. It does not publish audio to a code review, certify subjective song quality, or claim external device reachability.

## Tickets

- [Ticket 01](ticket-01-durable-lyrics.md): admission, actual lyric task execution, validated result, and owner review.
- [Ticket 02](ticket-02-decision-and-render.md): later exact approval, separate render, private audio, and guarded replay.
- [Ticket 03](ticket-03-recovery-and-qualification.md): failure, restart, cancellation, isolation, and independent full-journey evidence.

## Exit evidence

Each scenario starts red and is exercised through the owner surface. The positive qualification uses a fresh supervised release and a real claimed lyric task, with observed task result lineage; supplying a manifest directly to the song authority is insufficient. It proves later approval and a separate render through non-silent, owner-playable audio. The negative scenarios test absence of side effects. The independent gate checks source hashes, formal contract, acceptance, restart, and a clean checkout. The release report may change the song journey from unqualified to exercised only when this evidence is present.

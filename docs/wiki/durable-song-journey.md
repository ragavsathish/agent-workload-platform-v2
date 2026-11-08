# Durable song journey

Cites the [personal song rules](personal-song.md), [source song service](../../raw/v1/song-service.py), [source lyric workspace effect](../../raw/v1/song-workspace-effect.py), [source manifest rules](../../raw/v1/song-generation.py), [source durable owner](../../raw/v1/durable-owner.ts), [source owner protocol](../../raw/v1/song-owner-server.ts), and [local release rules](local-release-readiness.md).

## Owner journey

The owner submits a bounded song brief, style, duration, and eligible workspace with one request identity. The accepted generation binds a stable lyric task identity to that request. A lyric worker opens the selected workspace, receives the brief, and produces a completed task result. The result must contain a structured, valid manifest with title, style, complete sectioned lyrics, duration, tempo, key, and meter. A generated text answer alone does not make lyrics ready. The owner can inspect the complete manifest and its digest while no audio exists.

The owner then makes a separate, explicit approval or rejection decision about that exact manifest digest. Reading the manifest, task completion, or asking for audio does not imply approval. Rendering can begin only after approval is recorded. The render receives precisely the approved manifest and produces one private artifact. The owner plays it through an owner-checked path. The generation may instead end with bounded lyric or render failure, rejection, or cancellation.

## Evidence and reconciliation

- A full-journey claim needs one attributable chain: owner request, accepted generation, lyric task admission, actual worker claim and completion, validated manifest, owner presentation, later approval, separate render admission and completion, stored audio, and owner playback. The lyric result must come from the claimed task. A test-supplied manifest cannot establish the chain.
- Request replay returns the same generation and lyric task. Task submission uncertainty is reconciled against that stable identity. No restart or repeated status read creates another lyric task or turns a completed task into fresh work.
- A completed task without a valid manifest is a lyric failure. An unavailable lyric owner leaves submission pending or the journey unqualified with an observable reason; it never presents invented lyrics for approval.
- A running task interrupted across restart is inspectable as uncertain. Recovery follows the task owner's explicit retry or reconciliation policy; mere restart does not silently repeat an external effect. Late completion after cancellation cannot make the generation playable.
- A render task identity binds to one approved manifest digest. Render refusal before approval, rejected or changed manifests, duplicate render requests, render failure, and cancellation retain the [personal song rules](personal-song.md).
- Every generation, decision, task result, and audio read is scoped to the authenticated owner. A foreign caller receives the same absence response as for an unknown generation and cannot cause a task, decision, or render effect.

## Qualification boundary

A local release may claim this journey only after a fresh supervised run proves the whole chain through owner-facing requests and actual services. Its evidence records the selected version and state identity, task and generation identities, manifest and audio digests, worker attempt, outcome, and owner read without exposing private text or credentials. Technical media checks establish a playable non-silent artifact; a separate listening judgment is needed for musical or lyric quality. External device reachability, private knowledge cutover, and publication to a code review have their own gates.

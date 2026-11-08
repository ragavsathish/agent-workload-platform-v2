# Slice 16 decisions

Cites the [plan](plan.md), [durable song journey](../../wiki/durable-song-journey.md), [source lyric workspace effect](../../../raw/v1/song-workspace-effect.py), [source owner protocol](../../../raw/v1/song-owner-server.ts), and [slice 09 decisions](../09-personal-song/decisions.md).

## D63: a claimed lyric task is required for a full song journey

The song journey is qualified only when a stable, accepted lyric task is claimed and its completed result yields the reviewed manifest. The pruned branch is counting a test-supplied manifest plus a real render as a full owner journey.

## D64: lyric result lineage is checked before review

A completed task must match the generation, owner, and lyric task identity, then pass manifest validation before its digest is offered for review. A malformed or unrelated result becomes a bounded failure. The pruned branch is treating any completion or free-form text as lyrics ready.

## D65: approval occurs after presentation as a distinct decision

The owner sees the full immutable manifest and then records approval against its exact digest before requesting render. The pruned branch is inferring approval from creation, task completion, a status read, or an unverified caller assertion.

## D66: uncertain work retains one task identity

Submission and restart reconcile using the stable lyric task identity. An uncertain active attempt remains inspectable and needs the task owner's recovery decision. The pruned branch is starting another lyric attempt after an uncertain response or restart.

## D67: local release claims follow direct evidence

The local song release result becomes exercised only after the entire owner-to-audio chain is observed in a fresh supervised run. A missing dependency or partial run stays unqualified with a reason. The pruned branch is promoting a component pass or injected lyric result to a release claim.

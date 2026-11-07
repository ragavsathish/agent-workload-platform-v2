# Slice 09: personal song

Cites [personal song rules](../../wiki/personal-song.md), [task lifecycle](../../wiki/task-lifecycle.md), [source event walk](../../../raw/v1/expanded-storming.md), [source owner review](../../../raw/v1/song-pr-workflow.md), and [decisions](decisions.md).

## Goal and boundary

Let an authenticated owner request lyrics, review and decide on one complete manifest, request a separate render, and play the resulting private audio. Make approval an enforced decision. The [scenarios](spec.feature) define observable behavior from fresh state.

This slice starts at a song request and ends at private playback, refusal, failure, or cancellation. It reuses the task authority and artifact references from [slice 02](../02-task-lifecycle/plan.md). It does not publish a song to a code review or automate the adjacent soundtrack process. No real owner content is needed for deterministic acceptance.

## Tickets

- [Ticket 01](ticket-01-request-and-lyrics.md): request admission, stable identities, lyric task, manifest validation, and owner queries.
- [Ticket 02](ticket-02-approval-and-render.md): explicit review decision, guarded render submission, and replay reconciliation.
- [Ticket 03](ticket-03-outcome-and-playback.md): bounded result projection, private audio, failure and cancellation behavior.

## Exit evidence

For each ticket, record a named failing acceptance run before the build, then passing scenarios and the independent verification gate. A deterministic renderer double proves task and artifact behavior. A separate deployed qualification may prove real audio after the renderer and artifact services are available; until then real audio quality is unverified. Technology and external prerequisites are in the [implementation note](../../../impl/personal-song-notes.md).

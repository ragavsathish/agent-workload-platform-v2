# Slice decisions

Cites [the plan](plan.md) and [tasking rules](../../wiki/tasking.md).

## D01: stable identity and canonical input

The ordinary v1 gateway mints a fresh request identity, while the owner
deduplicates by identity and serialized input. The v1 C2 contract marks stable
caller identity as agreed. V2 completes that agreed behavior in this slice:
the caller supplies the stable identity; canonical owner, trimmed instruction,
and trimmed workspace determine whether a retry is the same request. Transport
tracing is excluded. The pruned branch was to preserve a fresh identity per
submission, which would make lost-response recovery impossible.

Source: [v1 source inventory](../../../raw/v1/manifest.md), C2-002, owner-client-002,
and the task admission source snapshot.

## D02: first slice ends at admission

The [plan](plan.md) stops at accepted-task visibility. Execution, progress,
result, and artifacts belong to later slices, so this slice cannot claim full
task parity. The pruned branch was to call a successful admission a completed
agent task.

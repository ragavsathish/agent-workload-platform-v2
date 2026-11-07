# Slice decisions and open questions

Cites [the slice plan](plan.md), [the lifecycle wiki](../../wiki/task-lifecycle.md),
and the [pinned v1 evidence](../../../raw/v1/manifest.md).

## D03: one execution mode first

This slice proves the selected workspace boundary and public task outcome
through one supported execution mode. The pruned branch was to require every
supported mode before the first lifecycle result could be verified. The
[C8 contract](../../../raw/v1/c8-agent-execution.feature) still requires the
public answer contract to remain stable as further modes are added.

## D04: a missing artifact does not rewrite completion

Artifact byte retrieval is deferred. A completed task may later have an
artifact lookup failure, but its terminal completion remains unchanged.
This follows [C2-005](../../../raw/v1/c2-tasking.feature). The pruned branch
was to make artifact availability part of the task's terminal status.

## D05: uncertain external effects require inspection

The [owner source](../../../raw/v1/durable-owner.ts) records that a resumed
non-idempotent effect needs inspection; it does not establish that every
external effect is exactly once. When execution may have begun but its outcome
is unknown, keep the task inspectable and require reconciliation before
repeating that effect. A controlled worker that proves retry safety may resume
under the same task identity. The pruned branch was unconditional re-execution
after a missing acknowledgement.

## D06: preserve the public state vocabulary

The [owner source](../../../raw/v1/durable-owner.ts) distinguishes pending,
running, sleeping, completed, failed, and cancelled states. The
[C2 contract](../../../raw/v1/c2-tasking.feature) names outcomes rather than
every intermediate state. The next slice preserves those owner-visible names;
an execution awaiting manual recovery remains pending with a separately
inspectable recovery condition. The pruned branch was to expose a new terminal
"parked" state that v1 does not report.

## Open: production worker retry classification

Before any real worker type uses automatic retry, determine which of its
external effects has a stable effect identity and which needs manual inspection.
The [execution source](../../../raw/v1/session-execution.ts) proves workspace
selection but does not prove exactly-once external effects. This question does
not block the controlled worker used to verify this slice.

# Decisions

Cites the [session behavior model](../../wiki/session-orchestration.md), the
[Slice 29 compaction decisions](../29-session-compaction-reconciliation/decisions.md),
and the [Slice 32 Pi evidence](../32-compaction-protocol-reconstruction/plan.md).

## D125 — Uncertainty retains the owner lock

The runtime lacks a distributed fencing token that proves every previous Pi
writer has stopped. An unchanged branch snapshot alone cannot prove a live
writer will not append later. Therefore a failed or interrupted effect is
recorded as uncertain, and the owner mutation lock remains held. Do not add a
blind owner/admin release or automatically apply a new compaction.

## D126 — Only Pi branch proof settles uncertain work

Retry may inspect the persisted branch and settle the original command only
when the existing Slice 32 predicate proves the new tail compaction entry is
parented to the recorded baseline with an increased entry count. This is
reconciliation, not a new Pi effect claim. Missing or divergent evidence keeps
the command uncertain.

## D127 — Uncertainty reason is owner-visible and bounded

Expose only a finite reason code such as `service-restarted`,
`pi-outcome-unproven`, or `pi-effect-failed`. Do not include provider errors,
Pi payloads, transcript, or branch contents in the projection. The owner sees
that the session is held and that retry only checks the persisted Pi branch.

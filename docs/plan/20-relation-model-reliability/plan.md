# Slice 20: relation proposal reliability

Cites [grounded relation rules](../../wiki/knowledge-relations.md), [reviewed quality rules](../../wiki/knowledge-quality.md), [pinned relation observations](../../../raw/relation-stage-observations.md), [decisions](decisions.md), and [scenarios](spec.feature).

## Goal and boundary

Make the bounded relation attempt reliably reach pending owner review when its model returns a valid, grounded candidate. Preserve a truthful, owner-scoped outcome when the model or proposal path fails. An explicit retry of a failed attempt uses a new identity while the prior attempt remains immutable.

The observed failure is one candidate returned by an isolated proposal run, followed by two complete proposal attempts that ended as failed, each with one recorded attempt and zero pending relations. A candidate from the isolated run is evidence that inference can occur; it is not evidence that the model-to-review path succeeded. The exact synthetic observation and diagnostic command are pinned in [the implementation diagnosis](../../../impl/relation-model-reliability-diagnosis.md). No private source content is part of this slice.

## Tickets

- [Ticket 01](ticket-01-diagnose.md): reproduce the complete proposal boundary and expose a safe, traceable failure category. Scenarios `reliability-001` and `reliability-002`.
- [Ticket 02](ticket-02-retry-and-stage.md): explicit retry identity, valid candidate staging, idempotence, rollback, and current review authority. Scenarios `reliability-003` through `reliability-006`.
- [Ticket 03](ticket-03-qualification.md): run a bounded synthetic live model attempt through the complete path and report the result honestly. Scenarios `reliability-007` and `reliability-008`.

## Exit evidence

Start the build with the [red replay](../../../impl/diagnostics/relation-stage-replay.clj), then add named acceptance checks that fail at the full proposal boundary before the fix. Verify a completed candidate reaches pending owner review through the real model path, with a durable attempt, trace, source and endpoint provenance, and no accepted graph edge. Verify failure, retry, idempotence, owner isolation, restart, and rollback separately. A successful synthetic run proves path integration only. Independently reviewed relation quality and private cutover remain governed by their own gates.

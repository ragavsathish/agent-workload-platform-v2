# Slice 13: task worker to owner delivery

Cites [task execution and owner delivery](../../wiki/task-execution-delivery.md), [task result delivery](../../wiki/task-result-delivery.md), and [decisions](decisions.md).

## Goal

Prove that an admitted task can be dispatched to a real worker, execute against its selected workspace, settle through result capture, and yield a bounded owner answer and downloadable work product. The [scenarios](spec.feature) cover both repository and scratch work, both execution modes, failure and restart boundaries, and owner isolation.

## Boundary

This slice begins with an admitted task and an approved workspace choice. It ends with owner-visible completion and a downloadable declared product, or an inspectable preparation, execution, or capture failure. Source publication and optional image preparation are prerequisites. External build services and separate-device access are qualified only where available; their absence is recorded as an unqualified boundary, never inferred from a fixture pass.

## Tickets

- [Ticket 01](ticket-01-source-and-dispatch.md): selected-source custody, task-specific preparation, image prerequisites, and dispatch handoff.
- [Ticket 02](ticket-02-worker-settlement.md): real worker execution, progress, completion, capture, and failure semantics.
- [Ticket 03](ticket-03-owner-journey-and-gate.md): owner result and download through a fresh public process, restart, isolation, and independent qualification.

## Exit evidence

Start each named acceptance scenario red. Exercise a real worker process for scratch and repository work; prove the bytes downloaded by the owner were made in the same selected task workspace. Include host and isolated execution evidence, a deliberately missing source or image prerequisite, late and duplicate callback refusal, restart without replay, failure on capture, and cross-owner denial. Report environmental gaps separately from passed scenarios.

# Slice 02: task lifecycle and execution

Cites [task lifecycle rules](../../wiki/task-lifecycle.md),
[task admission rules](../../wiki/tasking.md), and the
[pinned v1 source inventory](../../../raw/v1/manifest.md).

## Goal

An accepted Agent Task starts bounded work, reports progress, and settles to a
queryable completed, failed, or cancelled outcome under its original identity.
The owner can recover an interrupted task without creating another identity.

## Scope

Extend the task admitted in slice 01 through one supported execution mode.
Cover dispatch, selected workspace capability, progress by cursor, terminal
result or failure, abort, interruption and owner retry, and owner-scoped reads
and controls. Execution receives the task selected at admission and returns
observations to that task. This slice does not decide admission again.

The owner-visible scenarios are [spec.feature](spec.feature). Each needs a
scenario-named acceptance test written while the implementation is red, plus
an independent journey through the slice interface with a controlled worker.
The worker must be able to succeed, fail, pause, and be interrupted; the test
must inspect durable task state across a fresh start. A separate capability
check must show that an unselected repository cannot be written through the
selected execution.

## Tickets

- [Ticket 01](ticket-01-state-and-progress.md): start, progress, and terminal
  outcome under one task identity.
- [Ticket 02](ticket-02-control-and-recovery.md): abort, interruption, retry,
  dispatch uncertainty, and owner isolation.
- [Ticket 03](ticket-03-capability-and-verification.md): selected workspace
  grant and independent lifecycle proof.

## Exit evidence

All scenarios pass. The task can be read during active work and after a fresh
start. A failed or cancelled task is never reported as completed. Progress
retains order and cursor meaning across a fresh start. An unauthorized caller
cannot inspect or control the task. An interrupted retry retains the task
identity. The capability check passes. Record all deviations in
[decisions.md](decisions.md).

## Deferred and unresolved

Artifact byte retrieval and its separate failure path, multiple execution
modes, and production cutover are later slices. The source does not prove
exactly-once external effects after a crash; this plan requires identity and
observable-state recovery, and [decisions.md](decisions.md) records the
reconciliation question.

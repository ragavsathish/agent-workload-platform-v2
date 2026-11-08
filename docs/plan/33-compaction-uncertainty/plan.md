# Slice 33: compaction uncertainty and terminal hold

Cites the [session compaction contract](../29-session-compaction-reconciliation/plan.md),
the [durable build contract](../../../BUILD-CONTRACT.md), the current
[Pi proof contract](../32-compaction-protocol-reconstruction/plan.md), and the
[session behavior model](../../wiki/session-orchestration.md).

## Goal

Make an unproven Pi compaction outcome explicit and owner-readable without
releasing the session lock or risking another Pi compaction call.

## Boundary

The Pi branch remains authoritative for whether compaction happened. The
durable owner event stream retains the command identity, baseline, and lock. An uncertain command
may settle only when the existing branch proof succeeds. There is no owner or
operator override that clears the lock. If neither completion nor absence of
an in-flight writer can be proved, the command remains held indefinitely.

## Acceptance

- A service restart that finds a pending compaction records it as uncertain
  with a bounded public reason while retaining `pending-compaction`.
- An outcome that cannot be proven from the Pi branch is recorded as uncertain
  with a bounded reason; the read model exposes status and reason only to its
  owner.
- Retrying the same uncertain identity re-reads Pi's branch and may settle the
  command only from the existing compaction-entry/baseline proof.
- An uncertain retry never receives a new Pi effect claim and never invokes
  `session.compact()` again.
- An uncertain command continues blocking turns, selection, attachment, and
  other compaction commands. No blind cancellation or lock-release path exists.
- Session and Pi integration tests cover restart, persisted-branch proof, no
  proof, no duplicate effect, owner isolation, and retained lock. The Flutter
  owner client renders the safe uncertainty status/reason.
- The durable contract makes the terminal hold policy explicit and maps every
  scenario to executable tests.

## Documents

- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build ticket](ticket-01-terminal-compaction-hold.md)

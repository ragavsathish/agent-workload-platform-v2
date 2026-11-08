# Slice 29: session compaction reconciliation

Cites the [session orchestration rules](../../wiki/session-orchestration.md),
the [session CQRS slice](../03-session-orchestration/plan.md), the durable
[build contract](../../BUILD-CONTRACT.md), the pinned [AWP source inventory](../../raw/v1/manifest.md),
and pinned [ORC evidence](../../raw/orc/manifest.md).

## Goal

Make manual context compaction a durable, owner-scoped owner event store command whose
external Pi effect cannot be silently repeated after a lost response or service
restart.

## Boundary

owner event store records command identity, the selected conversation, and the Pi branch
cursor before invoking compaction. Pi remains authoritative for whether a
compaction entry was appended. owner event store records completion only after that entry
is observed. An ambiguous pending command stays visible and blocks other
session mutations; the system never invokes Pi a second time for that command.
This slice does not claim exact provider token accounting or recover an
external provider call that did not append a Pi compaction entry.

## Acceptance

- Rejected commands (no selection, stale context, foreign owner, conflicting
  identity, or failed post-turn revalidation) produce no pending compaction
  fact. A request during an active turn is queued with its command identity;
  it is promoted after turn settlement only after owner, selection, eligibility,
  and session state are revalidated.
- One accepted command ID records a pending operation and baseline Pi leaf
  before any Pi compaction call. Identical retries return the same operation;
  changed input under that ID conflicts.
- Pi appends at most one compaction for a command. If owner event store settlement is lost
  after Pi appended a compaction child of the recorded baseline, retry or
  restart reconciliation records completion without another Pi call.
- If Pi's branch does not prove either no effect or the expected compaction,
  the operation remains uncertain and is never reapplied automatically.
- A pending or uncertain compaction blocks turn admission, selection, attach,
  detach, and another compaction for that owner. Completion releases the lock.
- A queued compaction holds its command identity and conversation across the
  active turn. It does not call Pi until the turn settles and promotion
  succeeds; a failed revalidation rejects the queued request without an effect.
- A later explicit compaction after new eligible context uses a distinct
  command identity.
- Context queries expose only safe compaction status and owner-scoped fields;
  replay, restart, projection rebuild, and another-owner reads are consistent.
- The owner client retries a lost request with its original idempotency key;
  a successful command clears that key for the next deliberate compaction.
  After page reload, the owner-scoped context query exposes the pending command
  identity/status so the client can resume it rather than inventing a new one.

## Documents

- [Domain rules](../../wiki/session-orchestration.md)
- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build ticket](ticket-01-compaction-command.md)

# Slice 31: session store bootstrap and projection rebuild

Cites the [session orchestration contract](../../wiki/session-orchestration.md),
the [session acceptance plan](../03-session-orchestration/plan.md), the
[root build contract](../../../BUILD-CONTRACT.md), and the current durable
[regeneration report](../30-session-compaction-regeneration/regeneration-delta.txt).

## Goal

Prove that session commands and queries use the selected durable event store,
that the projection is reconstructed after close and reopen, and that bootstrap
does not expose stale process-local read state.

## Boundary

This slice verifies the current session runtime through its public system API.
It does not create parallel storage or duplicate service wiring. Command facts
remain authoritative; selection, history, transcript, context, turn, and routing
reads remain owner scoped. The root build contract records the selected stack
and runtime ownership obligations.

## Acceptance

- Opening a session system validates and registers its command, event, query,
  and projection contracts before accepting traffic.
- Commands append durable session facts; owner queries read the projection built
  from those facts.
- After closing and reopening the system on the same storage directory, reads
  reconstruct selection, transcript, turn, context, and routing state from the
  durable facts.
- A different owner cannot read the reconstructed owner's state.
- Startup recovery marks unfinished external turns uncertain and removes an
  ephemeral task attachment before reads or new commands are admitted.
- The new acceptance scenario is tied to the session test suite and durable
  event-model contract; Slice 30's report distinguishes the implementation
  available in v2 from the intentionally fenced clean-room workspace.

## Documents

- [Acceptance scenarios](spec.feature)
- [Decision log](decisions.md)
- [Build ticket](ticket-01-session-store-rebuild.md)

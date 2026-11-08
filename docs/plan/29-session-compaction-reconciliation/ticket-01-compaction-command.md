# Make context compaction a reconciled command

Cites the [slice plan](plan.md), [scenarios](spec.feature), root
[build contract](../../BUILD-CONTRACT.md), pinned [AWP v1 source inventory](../../raw/v1/manifest.md),
and pinned [ORC examples](../../raw/orc/manifest.md).

Carry a stable command identity from the client to owner event store. Persist a pending
command and Pi branch cursor before the effect, serialize owner session
mutations while it is unresolved, and settle from the exact appended Pi
compaction entry. Queue requests made during an active turn using the same
identity, then revalidate trusted owner, selection, eligibility, and session
state after settlement before promoting and invoking Pi. Replay and restart
may reconcile that durable entry but may not issue a second provider-backed
compaction for a pending command. Add fault-injected coverage for settlement
loss and ambiguous branch evidence.

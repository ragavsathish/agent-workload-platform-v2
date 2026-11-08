# Make context compaction a reconciled command

Cites the [slice plan](plan.md), [scenarios](spec.feature), the current
[Pi compaction call](../../../../impl/runtime/products/voice-agent/apps/pi-agent/voice-agent/voice-agent.ts),
and the [owner event store session event model](../../../../impl/src/awp_v2/session/event_model.clj).

Carry a stable command identity from the client to owner event store. Persist a pending
command and Pi branch cursor before the effect, serialize owner session
mutations while it is unresolved, and settle from the exact appended Pi
compaction entry. Replay and restart may reconcile that durable entry but may
not issue a second provider-backed compaction for a pending command. Add
fault-injected coverage for settlement loss and ambiguous branch evidence.

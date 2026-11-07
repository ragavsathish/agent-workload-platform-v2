# Ticket 03: capability and verification

Cites [the slice plan](plan.md), [scenarios lifecycle-001 and
lifecycle-008](spec.feature), and [the execution boundary](../../wiki/task-lifecycle.md).

Prove that execution has only the selected workspace capability and cannot
write through an unselected repository grant. Run a controlled worker through
the slice interface, inspect task state across a fresh start, and independently
ratify every scenario. Record any gap in [decisions.md](decisions.md).

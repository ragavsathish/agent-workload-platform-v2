# Ticket 02: bounded investigation request

Cites [slice plan](plan.md), [incident rules](../../wiki/operational-incidents.md), [source monitor](../../../raw/v1/stack-alert-monitor.py), and [task admission rules](../../wiki/tasking.md).

Capture only approved rule meaning and bounded safe metric samples. Preserve explicit unavailability and exclude arbitrary alert content. Record one immutable instruction and stable request identity per epoch before task submission. On timeout or restart, retry that identity and instruction through the task authority. Never infer that a second task is needed merely because an acknowledgement was lost.

Acceptance: [incident-001](spec.feature), [incident-007](spec.feature), and [incident-008](spec.feature).

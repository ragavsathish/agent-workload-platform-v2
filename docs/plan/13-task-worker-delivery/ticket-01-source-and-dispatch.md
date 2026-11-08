# Ticket 01: selected source and dispatch

Cites [the slice plan](plan.md), [scenarios](spec.feature), [task execution rules](../../wiki/task-execution-delivery.md), and [pinned image handoff](../../../raw/v1/task-image-ensure.py).

Prepare a task-specific workspace from the approved source, or an empty scratch workspace. Bind source identity and workspace identity to the admitted task. Check optional declared image prerequisites before starting an isolated attempt. Dispatch through the task authority and hand only the selected task workspace and verified image to its worker. A missing prerequisite fails the original task boundedly. Cover `delivery-002`, `delivery-003`, and `delivery-004`.

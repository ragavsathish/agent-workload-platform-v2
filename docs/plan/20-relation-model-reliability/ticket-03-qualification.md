# Ticket 03: qualify the live path

Cites [the plan](plan.md), [scenarios `reliability-007` and `reliability-008`](spec.feature), and [reviewed quality rules](../../wiki/knowledge-quality.md).

Run an owner-safe synthetic source through the actual bounded model and proposal path. Record elapsed time, model and prompt versions, trace availability, attempt status, pending count, and current count. A pass requires a completed trace-backed attempt and grounded pending relation in the same run. Report timeout, invalid, or failed runs as open with a safe failure category. Do not convert this path check into a claim of semantic accuracy or private cutover.

# Ticket 02: retry, stage, and preserve decisions

Cites [the plan](plan.md), [scenarios `reliability-003` through `reliability-006`](spec.feature), and [owner review rules](../../wiki/knowledge-relations.md).

Add a deliberate retry identity to the bounded attempt. Keep same-identity replay idempotent and changed-content conflict visible. A successful retry stages grounded candidates once as pending, while failure and invalid output create no relation. Check owner isolation, restart, current reads, and rollback to an earlier model configuration without replaying or rewriting accepted decisions.

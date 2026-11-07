# Ticket 01: completed-turn intake

Cites the [slice plan](plan.md), [knowledge rules](../../wiki/personal-knowledge.md), [source listener](../../../raw/v1/session-listener.ts), and [source task service](../../../raw/v1/task-service.py).

Observe only a settled owner message from the current conversation. Admit one source identity and submit journal and classification work separately through the existing task authority. Record each task's identity, submission uncertainty, progress, completion, or failure. Repeated observation reconciles existing work. Ignore incomplete and non-owner messages. Do not backfill old turns implicitly.

Acceptance: [knowledge-001](spec.feature), [knowledge-002](spec.feature), and [knowledge-003](spec.feature). Evidence includes a retry after uncertain submission and reversed sibling completion order.

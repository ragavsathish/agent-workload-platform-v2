# Ticket 03: owner incident history and outcome

Cites [slice plan](plan.md), [incident rules](../../wiki/operational-incidents.md), [task lifecycle](../../wiki/task-lifecycle.md), and [source C4 contract](../../../raw/v1/c4-operational-incidents.feature).

Expose active and resolved incident epochs with observation freshness, evidence status, stable request identity, task reference, and current task status. Show a finding only for a successful investigation; retain failure and unjudged states. Scope list and direct reads to the authenticated owner, including the task join. Rebuild the query from decision history without creating work.

Acceptance: [incident-009](spec.feature) and [incident-010](spec.feature).

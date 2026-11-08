# Ticket 04: replay and separate readiness gates

Cites the [plan](plan.md), [quality gate](../17-knowledge-quality/ticket-03-gate.md), [shadow gate](../18-private-knowledge-continuity/ticket-03-rollback-and-gate.md), and [scenarios](spec.feature) `workbench-011` through `workbench-013`.

Refuse foreign-owner and stale-revision actions at the decision boundary. Rebuild both queue projections from the ordered histories and compare before and after restart. Generate private row-level readiness and discrepancy reports plus disclosure-reviewed aggregate statuses. Ordinary review must not consume a locked holdout, publish an evaluation answer as knowledge, or switch live reads. Verify the two gate statuses independently and preserve explicit later owner approval for live selection.

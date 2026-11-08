# Ticket 01: diagnose the full proposal boundary

Cites [the plan](plan.md), [scenarios `reliability-001` and `reliability-002`](spec.feature), and [failed attempt rules](../../wiki/knowledge-relations.md).

Reproduce a model failure at the same boundary that records the attempt and stages a pending relation. Preserve a fast synthetic replay with a paired valid candidate control. Inspect the bounded live trace through a redacted diagnostic and identify whether the failure originates in model execution, output interpretation, or persistence. Expose only a safe category and trace reference in owner history. Do not infer success from a candidate returned by a separate run.

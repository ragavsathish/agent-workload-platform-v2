# Slice 19: grounded relation proposals

Cites [grounded relation rules](../../wiki/knowledge-relations.md), [taxonomy rules](../../wiki/knowledge-taxonomy.md), [reviewed quality rules](../../wiki/knowledge-quality.md), [decisions](decisions.md), and [scenarios](spec.feature).

## Goal and boundary

Let a bounded model suggest relations between already accepted owner concepts, with evidence for the edge itself. Stage suggestions for separate owner review and project only accepted, current decisions into owner graph reads. This closes the relation-proposal gap identified by the [reviewed quality slice](../17-knowledge-quality/plan.md).

The slice begins with an owner source and two accepted concepts. It ends with an empty or pending model result, an owner decision, and a scoped current graph or private quality observation. It covers subject hierarchy and association only. Factual personal predicates, inference from graph proximity, and automatic model publication are outside this slice.

## Tickets

- [Ticket 01](ticket-01-bounded-proposal.md): bounded relation discovery, exact edge evidence, model provenance, and visible failure outcomes. Scenarios `relation-001` through `relation-004`.
- [Ticket 02](ticket-02-review-and-projection.md): idempotent staging, owner accept/reject/correct/revoke, replay, projection, and isolation. Scenarios `relation-005` through `relation-010`.
- [Ticket 03](ticket-03-quality.md): relation branch through the private reviewed quality gate and honest open status. Scenarios `relation-011` and `relation-012`.

## Exit evidence

The builder records named red scenarios, then passing scenarios through public owner boundaries, restart/rebuild equality, an attempted second-owner read, and the independent verifier and clean-room fence. Run the bounded model on a safe source to establish actual inference, then report semantic quality separately. Without independently reviewed relation labels and frozen thresholds, the relation quality verdict remains open. Implementation choices and commands belong in [implementation notes](../../../impl/grounded-relations-notes.md).

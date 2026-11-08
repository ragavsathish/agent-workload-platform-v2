# Slice 17: independently reviewed owner-knowledge quality

Cites the [quality rules](../../wiki/knowledge-quality.md), [decisions](decisions.md), [taxonomy plan](../10-knowledge-taxonomy/plan.md), [extraction and migration plan](../08-knowledge-extraction-migration/plan.md), and [scenarios](spec.feature).

## Goal and boundary

Turn the existing open semantic-quality gate into a reproducible, privacy-safe owner review and qualification path. Start with source and feedback provenance, independent labels, and a frozen policy. End with a branch-specific measured report and an explicit pass, fail, or open verdict. The existing owner review stream remains the publication authority; this slice never promotes an evaluation label into current knowledge.

The present evidence establishes a structural evaluator and a bounded model smoke, but not independently reviewed quality. The inspected private drafts are pending. Therefore the planning baseline is **open**. The implementation must report readiness honestly if review is still unavailable.

## Tickets

- [Ticket 01](ticket-01-review-corpus.md): prepare private candidate records, owner/reviewer decisions, privacy approval, adjudication, and split manifests without exporting source content.
- [Ticket 02](ticket-02-evaluator.md): freeze scoring and baseline, evaluate each production proposal path against reviewed cases, and retain row-level evidence privately.
- [Ticket 03](ticket-03-gate.md): enforce precommitted thresholds, one-time holdout, reporting, restart/replay, and rollback; keep migration and deployment independent.

## Exit evidence

Record named acceptance scenarios red before implementation, then passing deterministic and live checks. A private readiness report must show branch counts, review status, family and duplicate checks, privacy checks, frozen digests, and missing coverage. A full quality claim additionally needs the actual bounded model path exercised on independently reviewed cases, per-branch metrics and confusion, repeat-run evidence, the single-use holdout decision, and an independent gate. The public report contains only reviewed aggregates. If review coverage is absent, the qualified outcome remains open even if every protocol test passes. Implementation guidance is in [notes](../../../impl/knowledge-quality-notes.md).

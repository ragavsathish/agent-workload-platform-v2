# Private owner review workbench

Cites the [reviewed quality rules](knowledge-quality.md), [private continuity rules](private-knowledge-continuity.md), [pinned journal review app](../../raw/v1/journal-review-mcp-readme.md), [pinned annotation finalizer](../../raw/v1/journal-annotation-finalize.mjs), and [slice 21 plan](../plan/21-private-review-workbench/plan.md).

An owner needs one local review place for two distinct queues. The quality queue holds candidate examples that may become evaluation reference answers. The continuity queue holds unlinked metadata from an old private knowledge snapshot. Each queue shows its own progress, unresolved reasons, and next permitted decision. Finishing one queue has no effect on the other's gate.

## Quality cases

Show the source and model suggestion together, with the exact selected evidence and its position in the source. A reviewer may record an expected item, an explicit empty answer, an explicit missed item, or an exclusion. Every expected item names its origin. A saved draft is still pending. Independent review identifies its actor and ordered decision. A dispute requires a different adjudicator, and privacy approval is a separate explicit action. An owner correction of current knowledge is visible as context but does not count as an independent evaluation label.

Assign a semantic family before placing a case in a development, challenge, locked holdout, or production shadow partition. Duplicate and near-duplicate sources in one family stay in one partition. The reviewer sees family members and any split conflict before submission. A newly found family link that crosses partitions invalidates readiness until resolved. A held case remains visible and cannot be silently dropped. A private readiness view reports case counts by branch, partition, review state, privacy state, family conflicts, negative cases, random samples, difficult patterns, and missing coverage.

## Unlinked metadata

Each frozen resource has a review card with its source snapshot identity, safe collection context, available source links, and quarantine reason. The owner may retain quarantine, link it to a verified source, or approve exclusion as non-knowledge metadata. A source link requires evidence that the frozen source actually contains the target and that the relationship is unambiguous. Exclusion requires an explicit owner decision, a reason, and evidence. A label alone is not a source link. The twelve known unlinked records begin unresolved; their count is an inventory fact, not twelve prefilled decisions. Every record remains in the accounting and rollback material after exclusion.

## Decision authority and privacy

Review actions are commands with stable action identities and expected revisions. Current case, family, and disposition screens are owner-scoped query projections rebuilt from ordered decisions. Private text, notes, source links, and row-level results stay in a restricted local workspace; shared decision history may carry only opaque identities and digests. A repeated command returns its prior receipt; a changed or stale command is refused. Restart replays the same decisions without changing their meaning. Another owner cannot query either queue.

The workbench cannot infer review or privacy approval from a saved field, model output, or completed page. It may prepare a private corpus and a disposition package only from eligible decisions, then run the existing readiness and shadow checks. It never publishes a model proposal, consumes the locked holdout during ordinary review, or chooses the live knowledge source. A private report gives the owner exact unresolved rows; a shared report contains disclosure-reviewed aggregates only. A quality verdict, shadow verdict, and live read choice remain separate decisions.

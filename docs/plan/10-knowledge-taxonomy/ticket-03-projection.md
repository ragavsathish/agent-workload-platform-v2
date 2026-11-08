# Ticket 03: scoped taxonomy and graph reads

Cites [slice plan](plan.md), [taxonomy rules](../../wiki/knowledge-taxonomy.md), [review rules](../../wiki/personal-knowledge.md), and [continuity rules](../../wiki/knowledge-extraction-migration.md).

Rebuild category and relation views from authoritative source, result, and ordered review records. Compare identity, category, endpoint, and accepted-state counts before enabling a rebuilt view. Search, traversal, export, and direct item reads require authenticated owner scope and current accepted-state checks. Revoked or corrected items and their stale relations cannot leak through a derived view. Verify both owner directions with similarly labeled data.

Acceptance: [taxonomy-006](spec.feature), [taxonomy-008](spec.feature), and [taxonomy-009](spec.feature).

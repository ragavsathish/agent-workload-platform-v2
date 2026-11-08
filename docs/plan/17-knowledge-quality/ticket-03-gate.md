# Ticket 03: qualification, replay, and rollback

Cites the [slice plan](plan.md), [quality rules](../../wiki/knowledge-quality.md), and [taxonomy decisions](../10-knowledge-taxonomy/decisions.md).

Apply the frozen coverage and numeric policy to each branch. An undefined rate, failed privacy or split check, runtime error, missing frozen artifact, or safety regression prevents promotion. Open the locked holdout once for a frozen candidate and retain the prior configuration for rollback. A changed policy or corrected label requires a new version and report; it cannot rewrite an old verdict.

Rebuild owner and evaluation views from their authoritative histories and verify that a pending, rejected, or revoked proposal does not become a published item after restart. A second owner cannot read another owner's private cases or current knowledge. Report model quality, private migration, and public deployment as separate gate fields.

Acceptance: [quality-007](spec.feature), [quality-008](spec.feature), and [quality-009](spec.feature).

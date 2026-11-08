# Private knowledge continuity

Cites the [legacy knowledge rules](knowledge-extraction-migration.md), [owner knowledge rules](personal-knowledge.md), [category rules](knowledge-taxonomy.md), [source review contract](../../raw/v1/c3-personal-knowledge.feature), and [slice 18 plan](../plan/18-private-knowledge-continuity/plan.md).

## Source, shadow, and authority

The prior knowledge store remains the owner read source while a frozen copy is imported into a separate shadow lineage. A snapshot identifies the exact source collections, resource identities, counts, and content digests. A fresh source digest must still match that snapshot before any switch decision; matching total counts alone is insufficient. A changed source requires a new snapshot and reconciliation, preserving the older attempt for audit.

Every source resource receives exactly one disposition: mapped with provable owner and source, quarantined with a reason, or explicitly excluded from owner knowledge by an owner-approved metadata classification. Exclusion still retains the original resource, digest, and count in the migration ledger. No unlinked resource becomes a fact or review action by inference. The observed frozen inventory contains 425 resources across four collections: 413 mapped and 12 unlinked journal metadata resources. Ten of the latter have no text or label; two have a label but no provable source. This describes their shape, not permission to discard them.

## Reconciliation

Reconciliation compares every collection and resource class, source identity, mapped owner, current meaning, and ordered review actions. It also compares the owner-visible lists of suggestions, accepted facts, and daily accounts. A daily account is compared with its date, time zone, source bundle, selected version, and all versions still represented in the source. A prior write that replaced a version cannot be reconstructed; the report names that historical gap instead of inventing an account.

The shadow view is read through the same owner boundary as the eventual selected view. Repeated import, process restart, replay of the decision history, and reconstruction of the derived category graph must yield the same owner-scoped results. A derived graph may contain retained old nodes internally, but owner queries intersect it with the current reviewed state. Unreviewed and revoked items remain absent from accepted reads.

## Reversible choice

A rehearsal may choose the shadow view in an isolated copy, compare it with the prior view, and choose the prior view again. Both histories and the frozen snapshot remain intact. A mismatch, changed source, unresolved metadata disposition, missing owner review, failed graph rebuild, or failed rollback keeps live reads on the prior source. A passing rehearsal produces a reviewable report; switching live owner reads requires a separate explicit owner approval. The [quality gate](knowledge-quality.md) and [external deployment gate](public-deployment.md) have independent verdicts.

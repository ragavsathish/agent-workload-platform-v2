# Slice 18 decisions

Cites [continuity rules](../../wiki/private-knowledge-continuity.md), [prior migration decisions](../08-knowledge-extraction-migration/decisions.md), [quality decisions](../17-knowledge-quality/decisions.md), and [source review contract](../../../raw/v1/c3-personal-knowledge.feature).

## D74: a current-source digest is a cutover prerequisite

Record an immutable snapshot identity and re-read the current source before qualification and again before a proposed live switch. Compare canonical content and collection counts. A drifted source starts a new shadow lineage; it cannot be reconciled by assuming unchanged counts mean unchanged content.

## D75: metadata has an explicit disposition

Retain the twelve unlinked journal metadata records in the private inventory. Classify each by evidence as a source-backed resource, an owner-approved non-knowledge metadata exclusion, or unresolved quarantine. A label alone does not establish provenance. Keep excluded records in accounting and rollback material. No implicit discard or acceptance is permitted.

## D76: parity is about owner meaning and history

Compare each owner-visible category, current review status, ordered actions, accepted facts, and daily account identity and versions. Preserve legacy identity and source provenance. A historical version absent from the prior store is an explicit unknown. Reconciliation of raw resource counts cannot substitute for this parity check.

## D77: shadow history and derived graph can be rebuilt

Import into isolated history with stable resource identities. Repeated import adds nothing. Restart and replay yield the same owner view. Rebuild the category graph from accepted owner decisions and filter current reads by those decisions. Candidate, rejected, and revoked content cannot become accepted through graph repair.

## D78: cutover approval follows a real rollback rehearsal

Exercise shadow selection, owner reads, and return to the prior source in isolation while preserving both histories. A discrepancy leaves live reads unchanged. Produce a redacted report with a frozen approval artifact. The actual live read choice requires an explicit owner decision after that artifact exists; neither a passing test nor a model-quality verdict issues that decision.

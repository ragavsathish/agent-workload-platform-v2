# Slice 08: knowledge extraction and migration

Cites [knowledge continuity rules](../../wiki/knowledge-extraction-migration.md), [personal knowledge rules](../../wiki/personal-knowledge.md), [source worker](../../../raw/v1/knowledge-processor.mjs), [source graph writer](../../../raw/v1/gateway-main.py), [source graph operations](../../../raw/v1/knowledge-graph-ops.py), and [decisions](decisions.md).

## Goal and boundary

Complete the path from the already admitted journal, classification, and daily tasks to validated, source-backed knowledge outcomes. Import the old private knowledge store with review history and source provenance, reconcile it against the original, then qualify a reversible owner-visible cutover. The [scenarios](spec.feature) define the acceptance contract.

The slice begins at task execution or a frozen legacy snapshot. It ends when results are incorporated once, reads reflect current review authority, the graph view can be rebuilt, and migration plus live extraction each pass their own gate. Turn intake, task admission, and review commands from slice 07 remain the authority. No live owner data is needed for the deterministic build tests.

## Tickets

- [Ticket 01](ticket-01-extraction-worker.md): execute and ingest journal, classification, and daily results with independent failures and source proof.
- [Ticket 02](ticket-02-graph-projection.md): build and repair owner-scoped knowledge projections from authoritative events.
- [Ticket 03](ticket-03-migration.md): inventory, snapshot, import, reconcile, rollback, and cutover of existing knowledge.
- [Ticket 04](ticket-04-quality-gates.md): qualify live model quality separately from old-data migration and record deploy evidence.

## Exit evidence

Record a failing named acceptance run before each build ticket, followed by passing scenarios and the independent gate. Supply a live model quality report with reviewed cases and explicit thresholds, and a separate real-data migration report with source/target reconciliation and rollback rehearsal. Missing model access or private snapshot access leaves the corresponding gate open. The [implementation note](../../../impl/knowledge-extraction-migration-notes.md) contains technology and topology choices.

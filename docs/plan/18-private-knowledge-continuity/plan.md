# Slice 18: private knowledge continuity

Cites [continuity rules](../../wiki/private-knowledge-continuity.md), [prior migration plan](../08-knowledge-extraction-migration/plan.md), [owner review rules](../../wiki/personal-knowledge.md), [source contract](../../../raw/v1/c3-personal-knowledge.feature), and [decisions](decisions.md).

## Goal and boundary

Turn the existing private snapshot preview into a non-destructive shadow import and owner-visible reconciliation. Begin with an authorized frozen source and an isolated target. End with a reproducible report of source drift, complete resource dispositions, owner-visible parity, replay and graph rebuild, and an exercised rollback. Live owner reads remain on their prior source. A live switch is a later, separately approved act.

The current frozen inventory has 2,915 source statements, 425 grouped resources, 413 mapped resources, and 12 quarantined journal metadata resources. The planning inspection found a matching live source digest at that instant. The build must check it again before qualification. The twelve include ten without text or label and two with a label; all lack a proven source link. Their classification requires evidence and owner review. The [scenarios](spec.feature) are the acceptance contract.

## Tickets

- [Ticket 01](ticket-01-snapshot-and-disposition.md): verify fresh source identity and inventory every resource, including the twelve unresolved metadata records.
- [Ticket 02](ticket-02-shadow-reconciliation.md): import into isolated history, compare owner-visible meanings and sequence, and prove replay and graph reconstruction.
- [Ticket 03](ticket-03-rollback-and-gate.md): rehearse selection and rollback in isolation, produce a redacted evidence report, and preserve an explicit approval boundary for any live switch.

## Exit evidence

Record named scenario failures before implementation, then passing scenarios and the independent repository gate. A private row-level ledger holds source and target identities, review order, daily versions, metadata dispositions, and discrepancies. The repository report contains only safe counts, opaque digests, and gate results. A clean shadow qualification proves a reversible candidate, not live cutover. Missing approval or any unresolved discrepancy keeps that gate open. Implementation details are in the [slice note](../../../impl/private-knowledge-continuity-notes.md).

# Slice 21: private owner review workbench

Cites the [workbench rules](../../wiki/private-review-workbench.md), [quality plan](../17-knowledge-quality/plan.md), [continuity plan](../18-private-knowledge-continuity/plan.md), [source evidence](../../../raw/v1/manifest.md#slice-21-private-review-workbench-evidence), [decisions](decisions.md), and [scenarios](spec.feature).

## Goal and boundary

Give the owner a usable local review place for the 120 pending classifier and journal candidates and twelve quarantined metadata resources. It must preserve source provenance, explicit human decisions, private row-level material, and separately measured quality and continuity readiness. The starting state is pending; this slice does not invent expected labels, provenance links, exclusions, or owner approvals.

The quality path begins with candidate inventory and ends with a private reviewed-corpus candidate or a named hold reason. The metadata path begins with a frozen quarantine inventory and ends with evidenced per-resource dispositions or unresolved quarantine. Each path can be resumed after interruption. Neither path changes live owner knowledge or live read choice. The [scenarios](spec.feature) are the acceptance contract.

## Tickets

- [Ticket 01](ticket-01-private-workbench.md): inspect, navigate, save, and resume both private queues, with owner scope and safe local output. Scenarios `workbench-001` through `workbench-003`.
- [Ticket 02](ticket-02-quality-review.md): label, privacy review, independent adjudication, family partitions, and eligibility. Scenarios `workbench-004` through `workbench-007`.
- [Ticket 03](ticket-03-metadata-disposition.md): evidence-backed metadata decisions, accounting, and shadow handoff. Scenarios `workbench-008` through `workbench-010`.
- [Ticket 04](ticket-04-gates.md): replay, owner isolation, separate gates, and reviewable private reports. Scenarios `workbench-011` through `workbench-013`.

## Exit evidence

Start named scenarios red in a separate build turn. A deterministic fixture exercises decisions, refusal, replay, and restart without private owner rows. A real local review session must open the pending inventories, show safe progress, save an explicitly entered decision, resume it, and produce a private readiness or disposition report; it must not silently qualify either gate. Verify restrictive workspace permissions and absence of source content in shared logs and repository files. Run the independent repository verifier and clean-room fence. An eligible quality corpus still requires actual human labels and coverage; a clean shadow still requires evidenced dispositions and later owner approval for live selection. Implementation choices and operational commands belong outside the plan.

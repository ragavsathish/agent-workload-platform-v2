# Ticket 01: selection and history

Cites [slice plan](plan.md), [conversation rules](../../wiki/session-orchestration.md),
and scenarios [session-001 through session-006 and session-016](spec.feature).

Record fresh and resumed selection under one owner, keep prior histories,
restore the latest selection after a fresh start, and project active and
historical reads plus a bounded catalog. Enforce owner-only read and selection
at the boundary. A reused command identity returns its original selection;
conflicting input refuses. Verify missing identity and busy selection refusal.
The public selection replies contain the chosen identity; the catalog is
bounded to 30 summaries with the active item first and the source-backed
public fields. Historical read returns a revisioned safe transcript without
changing selection.

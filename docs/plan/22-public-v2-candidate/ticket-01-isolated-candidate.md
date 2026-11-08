# Ticket 01: isolated candidate and preflight

Cites [slice plan](plan.md), [candidate rules](../../wiki/public-v2-candidate.md), [scenarios](spec.feature), and [host observation](../../../raw/v1/public-candidate-host-observation.md).

Inventory the running service's occupied addresses, state, entry identity,
and shared resources. Allocate a candidate with independent identities and
refuse any collision before start. Verify the existing service before and
after candidate start and restart. Refuse exposure if the candidate entry is
unverified or below the current safe version. Acceptance: `candidate-001`
through `candidate-003`.

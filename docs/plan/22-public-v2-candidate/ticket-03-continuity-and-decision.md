# Ticket 03: continuity and release decision

Cites [slice plan](plan.md), [candidate rules](../../wiki/public-v2-candidate.md), [scenarios](spec.feature), and [public deployment decisions](../06-public-deployment/decisions.md).

Restart the candidate and show the same selected turn and accepted task
without replay. Exercise a failing replacement and restore the last
qualified version at the candidate origin, preserving those identities.
Pair a foreign denial with a passing exact-owner journey on a healthy
candidate. Produce a release decision that names every missing external
prerequisite and leaves unsupported gates open. Verify the existing service
remains healthy throughout. Acceptance: `candidate-008` through
`candidate-011`.

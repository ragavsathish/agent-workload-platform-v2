# Ticket 02: owner relation review and current graph

Cites [the slice plan](plan.md), [review rules](../../wiki/knowledge-relations.md), and [scenarios](spec.feature) `relation-005` through `relation-010`.

Stage valid candidates idempotently as pending proposals. Reject changed retries and invalid endpoints. Require owner and current revision for acceptance, rejection, correction, and revocation. A correction rejects and links the original to a new pending proposal. Keep ordered history and evidence. Project only accepted edges whose endpoints are still accepted; cycle checks and owner scoping apply at decision and query. Rebuild and restart must agree, including all owner read paths. Acceptance: `relation-005` through `relation-010`.

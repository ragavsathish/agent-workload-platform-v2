# Decisions

Cites the [slice plan](plan.md), [task request rules](../../wiki/tasking.md),
and the [pinned v1 source inventory](../../../raw/v1/manifest.md).

## D01 — Keep the request identity while the outcome is uncertain

The owner form derives a stable key from the exact canonical instruction in
the pending request. A transport or response error keeps that key so another
submission of the same instruction can recover the accepted task. A changed
instruction receives a separate pending key. The key is removed only when a
successful response confirms the task; submitting again after confirmation
creates new work with a new identity.

## D02 — The existing admission authority remains the writer

The gateway maps the idempotency key to the existing request identity and
submits it to the existing admission command. Ownership, eligibility,
canonical input, conflict behavior, and task identity remain in that authority.
This slice does not duplicate admission in the client or a second ledger.

## D03 — The owner retries only an unconfirmed request

The first named owner journey failed because the task submission carried no
stable request key. The client now retains the key after an uncertain response
and drops it after receiving the accepted task. A later deliberate submission
of the same instruction therefore receives a new key.

# Ticket 01: owner retry identity

Cites the [slice plan](plan.md), the [acceptance
scenarios](spec.feature), the [task request rules](../../wiki/tasking.md), and
the [pinned v1 task request contract](../../../raw/v1/c2-tasking.feature).

Carry a stable request identity from the owner task form through the owner
request boundary to the existing admission authority. Retain the pending key
when the response is uncertain; clear it only after the accepted task is
returned. A later deliberate submission gets a new key. Prove the behavior
through the owner workspace and the server task route.

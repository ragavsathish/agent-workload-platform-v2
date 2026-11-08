# Ticket 02: project and retrieve owned results

Cites [slice plan](plan.md), [rules](../../wiki/task-result-delivery.md), [task service](../../../raw/v1/task-service.py), and [service tests](../../../raw/v1/task-service-test.py).

Project a bounded answer and artifact-kind list from the terminal result. For artifact reads, check owner, task identity, selected workspace kind, exact declaration, object size, and complete bytes before returning content. Keep storage addresses and raw worker records out of public task reads. Report retrieval failure without changing terminal completion. Cover scenarios `artifact-005` through `artifact-008`.

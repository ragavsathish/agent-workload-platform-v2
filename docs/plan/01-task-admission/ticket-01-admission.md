# Ticket 01: admit and query tasks

Cites [the plan](plan.md), [scenarios admission-001 through admission-006](spec.feature),
and [tasking domain rules](../../wiki/tasking.md).

Define a single admission decision that validates the caller and input,
returns an existing task for an identical retry, refuses a conflicting retry,
and creates one accepted task for a new eligible request. Expose an owner-scoped
query. Preserve identity under overlapping submissions. Write acceptance tests
from the named scenarios while red, then implement the behavior.

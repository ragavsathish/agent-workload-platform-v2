# Ticket 02: control and recovery

Cites [the slice plan](plan.md), [scenarios lifecycle-005 through
lifecycle-008](spec.feature), and [the lifecycle wiki](../../wiki/task-lifecycle.md).

Abort an active task without allowing a late completion to replace the
cancellation. Recover uncertain dispatch and an interrupted task under the
original identity, with prior progress intact. Require owner identity at every
state, progress, result, abort, and retry boundary. Write acceptance tests for
the named scenarios while the implementation is red.

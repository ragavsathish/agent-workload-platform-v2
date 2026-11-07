# Ticket 01: state and progress

Cites [the slice plan](plan.md), [scenarios lifecycle-001 through
lifecycle-004](spec.feature), and [the lifecycle wiki](../../wiki/task-lifecycle.md).

From one accepted task, expose an active state, bounded cursor-addressable
progress, and a terminal completion or failure. Keep the task identity stable
through the transition. An owner read during active work must return promptly.
Fresh-start checks must recover the terminal state and progress. Write the
scenario-named acceptance tests before the behavior that passes them.

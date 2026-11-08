# Task workspace

Build owner-scoped task creation, list, progress, cancel, attachment, and
artifact retrieval. Route task state and progress through owner-scoped queries; do not
read a projection directly from the screen. Treat artifact bytes as a separate
authorized retrieval and preserve the task outcome on a retrieval failure.

Add an owner-scoped task-list query to the existing task read contract. Its
result must be the same ordered task state shown after reload and must not
include another owner's task.

Done when task and conversation identities remain separate across reload,
attachment, cancellation, and result retrieval.

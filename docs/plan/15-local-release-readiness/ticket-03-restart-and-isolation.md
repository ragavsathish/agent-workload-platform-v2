# Ticket 03: restart, recovery, and owner separation

Cites the [slice plan](plan.md), [scenarios](spec.feature),
[conversation rules](../../wiki/session-orchestration.md),
[task lifecycle](../../wiki/task-lifecycle.md), and
[knowledge rules](../../wiki/personal-knowledge.md).

Restart the supervised conversation and executor services with the same
private state. Reopen owner reads for the selected conversation, task result,
product declaration, and review history. Confirm a settled turn is not sent
again and a claimed uncertain task effect waits for explicit owner retry.
Attempt foreign reads and commands against all five owner domains through
their actual routes. Record denials and verify no new accepted action was
created. Cover `release-007` through `release-009`.

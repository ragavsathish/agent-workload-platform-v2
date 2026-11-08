# Task routing

Cites the [conversation rules](session-orchestration.md), the [task rules](tasking.md), and the [captured v1 journey](../../raw/v1/manifest.md).

Task routing temporarily directs later owner turns to an eligible durable task. It does not change the selected conversation or give the task's conversation to the orchestrator history.

Only the task owner may attach or detach a task. The task must remain eligible while routed. The owner can inspect which task currently receives turns.

Attachment and detachment cross the conversation authority and the live task connection. The owner-facing command reports success only after both sides agree. If an effect outcome cannot be confirmed, the route is uncertain and later turns must be held until the owner or recovery path reconciles it.

Starting or resuming a conversation releases the temporary route. Closing the voice connection also releases it. A restarted service reports detached routing; it does not reopen a task connection from a prior fact.

Retrying a completed command with the same identity and input returns its prior outcome. Reusing that identity with different input is refused. An artifact or terminal task outcome does not change through attachment.

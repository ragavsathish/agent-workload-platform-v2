# Owner workspace

Cites the [captured v1 client source](../../raw/v1/client-source-capture.md),
the [conversation rules](session-orchestration.md), the [live conversation
rules](live-session-integration.md), the [task rules](tasking.md), and the
[task lifecycle](task-lifecycle.md).

The owner workspace is the private surface for conversation and bounded work.
It lets the owner select or resume a conversation, read its safe history,
continue with typed or spoken turns, and interrupt an active turn. The active
conversation identity is visible. A partial or interrupted turn is not
presented as a completed answer.

The workspace also lets the owner create and inspect durable tasks, follow
bounded progress, cancel eligible work, and attach a task to a conversation for
future turns. Task acceptance and task outcome remain separate from the
conversation. Artifact retrieval is a separate owner-scoped action; an
unavailable artifact does not change a completed task's outcome.

Context use is visible and maintenance is explicit. A conversation switch does
not recreate the service's tool environment or change an accepted task's
identity. Only safe transcript and presentation content is shown. Private
reasoning, tools, credentials, and another owner's history are never exposed.

The same workspace behavior is available in a browser and in a native shell.
The shell stays on its configured private workspace. Microphone permission is
requested only when the owner starts a spoken conversation; camera access is
not needed. A response may contain a bounded interactive presentation, shown
without granting it access to the workspace, owner credentials, or task
controls.

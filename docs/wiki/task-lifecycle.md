# Task lifecycle and execution

Cites the [pinned v1 source inventory](../../raw/v1/manifest.md), especially the
[C2 task contract](../../raw/v1/c2-tasking.feature),
[C8 execution contract](../../raw/v1/c8-agent-execution.feature),
[durable task journey](../../raw/v1/voice-to-durable.feature), and
[owner lifecycle tests](../../raw/v1/durable-owner.test.ts).

An **Agent Task** has the identity established by its **Task Request**. Execution
may continue after the owner's original interaction ends. The owner can inspect
the task's progress and terminal outcome under that same identity. The task's
execution has a selected workspace capability; another repository is not
implicitly writable from that task. [C2-001, C8-001, C8-002]

## Lifecycle rules

- An accepted task can be awaiting execution, running, or terminal. A terminal
  task is completed, failed, or cancelled. Only completion carries a successful
  result; a failure remains a failure attached to the task identity. [C2-004,
  owner lifecycle tests]
- Progress is a bounded, owner-visible account with a monotonically increasing
  cursor. Reading after a cursor returns later items without waiting for an
  active execution to finish. Progress must not expose sensitive tool arguments
  or private reasoning. The pinned owner keeps at most 2,000 items per task,
  truncates each summary to 240 characters, and limits a read to 100 items.
  [Durable task journey, owner lifecycle tests, durable owner, progress narration]
- The owner can abort active work. An aborted task becomes terminally cancelled;
  a late execution answer must not make it completed. [Owner lifecycle tests]
- A stopped execution remains inspectable under its original task identity.
  When automatic recovery cannot continue it, the owner can request retry on
  that identity. The prior result and progress are not silently rewritten.
  [Owner lifecycle tests]
- An accepted task whose dispatch acknowledgement is uncertain remains the
  same task. Retrying the Task Request may resume dispatch, without a new task
  identity. This is agreed behavior that the next slice elects to complete.
  [C2-007]
- Task state, progress, control, and result are owner scoped. Another owner
  gets a bounded missing-task answer and no task details. [C2-003, owner client
  contract]

## Boundaries

The selected workspace is a grant to one execution, not a general grant over
every repository. The execution outcome returns to the owning task; execution
does not decide whether a new request should be admitted. [C8-001, C8-002]

The [next slice](../plan/02-task-lifecycle/plan.md) covers one supported
execution mode and the task's visible lifecycle. Additional modes, artifact
retrieval, and broad delivery remain later slices. Missing artifact bytes do
not retroactively change a completed task's outcome. [C2-005, C8-003]

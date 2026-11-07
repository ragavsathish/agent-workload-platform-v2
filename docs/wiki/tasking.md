# Task request and agent task

Cites the [v1 source inventory](../../raw/v1/manifest.md), especially the C2
contract, owner client contract, glossary, and task admission source.

**Task Request** is an owner's intent to delegate one bounded unit of work.
**Agent Task** is the accepted work with an identity the owner can inspect.
Repeating the same request after an uncertain response refers to the same task;
deliberately new work requires a new request identity. These terms come from
the glossary snapshot.

## Admission rules

- An authenticated owner may submit a nonempty instruction for an eligible
  workspace. An accepted request receives one task identity. [C2-001]
- A repeated stable request identity with the same canonical owner, instruction,
  and workspace returns the existing identity and creates no second task.
  This is agreed behavior that v2 elects to complete. [C2-002, owner-client-002]
- Reusing a request identity with different canonical input is refused with a
  bounded reason. An ineligible workspace is refused. [C2-003]
- The owner may query an accepted task. Another owner cannot admit work under
  that owner or inspect its task. [C2-001, C2-003]
- If admission commits and its response is lost, retry still returns the same
  task identity. Admission does not claim execution has begun. [C2-002, C2-007]

Canonical input for this slice trims the instruction and workspace and
normalizes the owner identifier. It excludes transport tracing. This is a v2
choice recorded in [decision 01](../plan/01-task-admission/decisions.md).

## Boundary

This slice ends at accepted task visibility. Progress, execution, terminal
result, and artifact lookup remain later behavior in the [C2 contract](../../raw/v1/c2-tasking.feature).

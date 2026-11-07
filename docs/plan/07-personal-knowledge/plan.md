# Slice 07: personal knowledge

Cites [personal knowledge rules](../../wiki/personal-knowledge.md), [current conversation rules](../../wiki/session-orchestration.md), [task lifecycle rules](../../wiki/task-lifecycle.md), [source C3 contract](../../../raw/v1/c3-personal-knowledge.feature), and [decisions](decisions.md).

## Goal and boundary

Carry a newly completed owner turn into two separately durable extraction outcomes, present attributed suggestions, record owner review without losing source or prior actions, and build a daily account from one explicit bounded source bundle. Keep the conversation answer independent of extraction success. Give owner queries a source-backed current interpretation while refusing foreign, stale, and ambiguous actions.

The slice starts at a settled turn in the current conversation and ends at owner-visible extraction outcomes, reviewed knowledge, or an attributed daily account. It reuses task admission and lifecycle authority; the build must not create a second task owner. Historical import, profile inference without review, and automatic daily scheduling are outside this slice. The [scenarios](spec.feature) are the acceptance contract.

## Tickets

- [Ticket 01](ticket-01-turn-intake.md): completed-turn intake, stable source identity, and independent task outcomes.
- [Ticket 02](ticket-02-review-and-reads.md): source-backed suggestions, versioned owner review, and owner-scoped queries.
- [Ticket 03](ticket-03-daily-account.md): explicit source bundle, stable request/version identity, account result, and qualification.

## Exit evidence

Each ticket records a failing named acceptance run before its build, then passes its scenarios and the independent gate. The evidence shows a settled turn creates exactly one journal and one classification task across repeated observation; either task can fail while the other succeeds; owner review preserves history and rejects stale or foreign commands; and changed daily sources create a distinct version. Use synthetic personal text in acceptance evidence. The [implementation note](../../../impl/personal-knowledge-notes.md) records technology and external dependency choices.

# Slice 12: task result and artifact delivery

Cites [task result rules](../../wiki/task-result-delivery.md), [task lifecycle](../../wiki/task-lifecycle.md), [C2 contract](../../../raw/v1/c2-tasking.feature), [artifact store](../../../raw/v1/task-artifacts.py), and [decisions](decisions.md).

## Goal and boundary

After a task finishes, its owner can inspect a bounded, public answer and download the exact patch or scratch workspace declared by that task. A missing artifact has an explicit retrieval failure and does not rewrite completed work. The [scenarios](spec.feature) define the observable contract.

The slice starts at an admitted task with one selected workspace and ends at a safe result projection, a successful bounded download, or a distinct execution or lookup failure. It does not admit another task, infer owner approval from a result, publish a repository on the owner's behalf, or automate a later change review.

## Tickets

- [Ticket 01](ticket-01-capture.md): bounded repository patch and scratch workspace capture, plus source identity and failure behavior.
- [Ticket 02](ticket-02-projection-and-query.md): safe answer projection, owner-scoped artifact declaration and retrieval, and precise missing-object outcome.
- [Ticket 03](ticket-03-delivery-and-gate.md): owner delivery route, visible download affordance, restart and independent integration proof.

## Exit evidence

Write named failing acceptance tests from every scenario before implementation. Exercise task completion through the production task interface with real artifact bytes, including a repository patch and scratch archive. Prove ownership, wrong kind, wrong task reference, size and incomplete-read refusal, missing bytes after completion, restart persistence, and public answer sanitization. The independent gate checks the public route and payload headers as well as the task's unchanged completion after retrieval failure. Record any environment-dependent source publishing or client qualification separately. Implementation topology belongs under `impl/`.

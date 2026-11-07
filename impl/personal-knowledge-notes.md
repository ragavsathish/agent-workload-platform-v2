# Slice 07 implementation notes

Cites the [slice plan](../docs/plan/07-personal-knowledge/plan.md), [decisions](../docs/plan/07-personal-knowledge/decisions.md), [source routes](../raw/v1/gateway-main.py), and [source task service](../raw/v1/task-service.py). These are implementation candidates, not domain rules.

## Candidate topology

Use the existing Grain event model and command/query split for source intake, task links, review actions, and daily version identity. Continue to use the current task admission and lifecycle APIs for the two extraction modes and daily builds. The completed-turn adapter should consume the settled current-session event from slice 03; it should persist a mode-specific submission record before invoking external effects, and reconcile unknown submissions under the same stable request key.

The v1 extraction processors use external LLM inference and write to Oxigraph through the Gateway. In v2, treat inference as a bounded effect that returns validated, source-linked suggestions. An Oxigraph adapter can serve a graph projection and provenance query, but Grain-owned review decisions and revisions should be authoritative; graph update failure must be repairable without inventing a second owner decision. Do not infer a profile fact from the model output. Maintain separate journal and classification processor state so either may fail independently.

For daily accounts, hash a canonical owner/date/time-zone/source bundle. Keep immutable account versions keyed by that identity. The current v1 Oxigraph writer deletes a date-and-zone page before inserting a new one; that strategy does not preserve prior source-set versions and should not define the v2 authority. An external writer can generate prose and a projection can expose the selected account version only after its source match is verified.

## Build checks

Add named acceptance tests from the slice feature to the harness during the build invocation, beginning with red. The implementation gate should check replay, simultaneous conflicting review commands, two owners, independent extraction failures, writer failure, source-bundle changes, and projection repair. Use synthetic transcripts and no live personal data in automated fixtures. Live LLM and existing private-store migration are separate qualification work.

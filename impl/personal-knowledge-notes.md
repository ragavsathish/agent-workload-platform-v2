# Slice 07 implementation notes

Cites the [slice plan](../docs/plan/07-personal-knowledge/plan.md), [decisions](../docs/plan/07-personal-knowledge/decisions.md), [source routes](../raw/v1/gateway-main.py), and [source task service](../raw/v1/task-service.py). These are implementation candidates, not domain rules.

## Candidate topology

Use the existing Grain event model and command/query split for source intake, task links, review actions, and daily version identity. Continue to use the current task admission and lifecycle APIs for the two extraction modes and daily builds. The completed-turn adapter should consume the settled current-session event from slice 03; it should persist a mode-specific submission record before invoking external effects, and reconcile unknown submissions under the same stable request key.

The v1 extraction processors use external LLM inference and write to Oxigraph through the Gateway. In v2, treat inference as a bounded effect that returns validated, source-linked suggestions. An Oxigraph adapter can serve a graph projection and provenance query, but Grain-owned review decisions and revisions should be authoritative; graph update failure must be repairable without inventing a second owner decision. Do not infer a profile fact from the model output. Maintain separate journal and classification processor state so either may fail independently.

For daily accounts, hash a canonical owner/date/time-zone/source bundle. Keep immutable account versions keyed by that identity. The current v1 Oxigraph writer deletes a date-and-zone page before inserting a new one; that strategy does not preserve prior source-set versions and should not define the v2 authority. An external writer can generate prose and a projection can expose the selected account version only after its source match is verified.

## Build checks

Add named acceptance tests from the slice feature to the harness during the build invocation, beginning with red. The implementation gate should check replay, simultaneous conflicting review commands, two owners, independent extraction failures, writer failure, source-bundle changes, and projection repair. Use synthetic transcripts and no live personal data in automated fixtures. Live LLM and existing private-store migration are separate qualification work.

## Build evidence at slice 07

The build uses a separate Grain knowledge stream with owner-tagged compare-and-set decisions. The adapter at `impl/src/awp_v2/knowledge/gateway_adapter.clj` speaks the pinned Gateway ingestion and daily-build task protocol; a local HTTP protocol test checks paths, owner/source payload, and service credential. The legacy ingestion route fans out both modes on each call, while the v2 knowledge stream tracks their task identities and outcomes separately. The adapter depends on the Gateway's stable source-key deduplication; a lost response is retried with the original source and mode identity. The session intake function reads only a settled, unattached owner turn from the current conversation projection. A startup reconciliation scans pending submissions.

An external processor must report a matching task identity and source-backed, evidence-anchored suggestions into the knowledge API. Daily writer reports must carry the exact source bundle used; a mismatched digest is refused. The build does not qualify the live model's extraction quality, replay an existing private graph, or synchronize the old graph's owner review records into the new stream. The legacy Gateway task result is a bounded status summary, not a trustworthy source for extracted private suggestions. These remain explicit integration and migration work before a live personal-knowledge cutover.

The v2 Grain bridge now observes a completed settled turn and submits its source to knowledge intake. A knowledge failure is contained after turn settlement, so the answer remains settled. Owner-authenticated v2 Gateway routes forward bounded knowledge reads, review actions, and explicit daily requests to the Grain knowledge service. Internal outcome and writer-result commands remain service-only. The optional legacy Gateway adapter is enabled only with an explicit URL and token; without it, submissions remain inspectably uncertain and reconcile when the adapter is configured.

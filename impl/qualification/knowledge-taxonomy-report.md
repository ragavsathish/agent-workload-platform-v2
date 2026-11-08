# Slice 10 knowledge taxonomy qualification

Cites [slice contract](../../docs/plan/10-knowledge-taxonomy/plan.md), [quality ticket](../../docs/plan/10-knowledge-taxonomy/ticket-04-quality.md), [prior integration evidence](../knowledge-extraction-migration-notes.md), and [taxonomy acceptance](../test/awp_v2/knowledge_taxonomy_acceptance_test.clj).

## Verified on 2026-11-08

- Eleven named acceptance scenarios cover category identity, mapping, evidence, owner review, relation decisions, replay, revocation, isolation, and quality-gate behavior. Additional tests exercise the bounded ORC candidate staging path, the shared Grain event store after restart, and distinct scoring errors.
- The restart test reopened the same event-store directory and read two accepted owner concepts and one reviewed relation. A pending and a rejected proposal remained absent from current owner reads. It observed both knowledge facts and ORC concept events in that store, then replayed the ORC projection without duplication.
- The independent repository verifier passed. It includes strict Grain Event Model validation, Allium checks, source hashes, runtime builds, and existing public probes.

## Open gates

**Live model quality: open.** No privacy-safe, independently reviewed taxonomy corpus or precommitted numeric thresholds was supplied. Synthetic tests establish scoring and protocol behavior only. The earlier bounded local ORC LLM smoke at `qwen3.5:4b-mlx` shows candidate production with exact source text, but does not establish semantic precision, recall, category accuracy, or relation accuracy. No pass verdict is recorded.

**Private knowledge cutover: open.** The prior migration snapshot has 12 quarantined metadata resources and no owner-authorized reconciliation and rollback qualification. Public tailnet deployment is a separate open qualification.

The taxonomy category set comes from pinned v1 evidence; hierarchy and version mapping are explicit v2 decisions. ORC retains append-only historical concepts and edges after revocation. The owner facade intersects every published read with current Grain review state; direct ORC access remains an internal capability and must not be exposed as a public owner endpoint.

# Slice 17 owner knowledge quality qualification

Cites the [slice plan](../../docs/plan/17-knowledge-quality/plan.md), [quality policy](knowledge-quality-policy-v1.json), [private corpus contract](knowledge-quality-corpus-v2.schema.json), [Grain ledger checks](../test/awp_v2/knowledge_quality_acceptance_test.clj), and [protocol checks](../runtime/products/voice-agent/apps/pi-agent/knowledge-quality-v2.test.mjs).

## Evidence

- The v1 ignored annotation drafts contain 80 classifier cases and 40 journal cases. All 120 are pending; both privacy reviews are pending. The journal draft is development-only and the classifier draft has no assigned split.
- A private candidate preparation wrote 120 pending rows with restrictive permissions outside the repository. It did not assign expected answers or approve any case. The v2 readiness check found **0 eligible cases**. The missing gates are independent review, privacy approval, valid assignments and anchors, and required branch/risk coverage.
- Nine named protocol scenarios pass. Grain review/run metadata survives restart, a second owner cannot query the first owner's cases, and a review label does not publish knowledge. The native production worker returned an empty result for a synthetic command without invoking a model.
- The final repository verifier passed: 145 Clojure tests with 564 assertions, all nine named quality protocol scenarios, the strict Grain and Allium checks, runtime acceptance suites, and the public module probe. The clean-room fence result is recorded in the slice commit handoff.

## Verdict

**Model quality: open.** No independently reviewed, privacy-approved corpus exists. The checked-in 40-case classifier fixture and 200 wording variants are regression examples without review or split provenance. No semantic precision, recall, category, or relation claim is made from them.

**Private knowledge cutover: open.** The earlier migration still has unresolved quarantined resources and needs owner-authorized reconciliation.

**Public tailnet deployment: open.** It remains an independent external-device qualification.

The bounded ORC candidate path currently proposes concepts only. Relation model quality cannot pass until a production relation proposal exists and has independently reviewed cases. The previous model configuration remains the rollback target for any future candidate run.

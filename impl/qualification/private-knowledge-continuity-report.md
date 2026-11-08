# Slice 18 private knowledge continuity qualification

Cites the [slice contract](../../docs/plan/18-private-knowledge-continuity/plan.md), [driver](../knowledge-migration/continuity.py), [Grain runner](../src/awp_v2/knowledge/continuity_tool.clj), and [nine named scenarios](../knowledge-migration/test_continuity.py).

## Real private shadow rehearsal, 2026-11-08

The loopback source matched the frozen canonical digest before and after import. The owner map reproduced the normalized package. The source had 2,915 statements in four collections and 425 grouped resources: 413 mapped and 12 quarantined. The twelve have no source reference; ten lack both text and label, while two have labels. None was promoted to owner knowledge.

The isolated Grain import reconciled **413 of 413 mapped resources** after a half-import interruption, resume, and repeat. Source-derived interpretation and shadow owner reads had **zero item, review, or daily discrepancies**. The package contains 32 journal items, of which 19 are active and 13 are deleted, plus 15 current daily pages. The pinned v1 owner-query shapes returned the same 19 active journal identities, categories, and labels, zero feedback actions, zero accepted facts, and zero pending reviews. Daily comparison included date, time zone, source bundle, and legacy version identity for each extant page. The source retains no proof of prior overwritten daily versions; their existence and contents remain unknown.

The shadow owner view survived Grain restart and ORC graph rebuild. Shadow selection and return to prior reads were exercised only in the isolated store; the prior view was restored and both histories remained. The real source has zero accepted concepts, so its graph check covered an empty accepted view. A separate synthetic test persisted one accepted concept across a Grain/ORC restart. All nine named acceptance scenarios passed.

The full repository verifier passed, including 145 Clojure tests with 564 assertions, the nine slice scenarios, strict Grain model validation, source hashes, runtime checks, and owner-boundary probes. The final clean-room fence reruns this verifier.

## Verdict and limits

**Shadow gate: open.** Twelve unlinked metadata records still require an evidenced, owner-approved disposition. No owner approval for live selection is recorded, and live owner reads remain on v1. The current source has zero actual review actions and zero accepted facts, so parity for populated histories was exercised with synthetic cases. Prior overwritten daily versions cannot be recovered from the current source. Model quality and external deployment have separate open gates.

Private row-level source, target, and discrepancy data remain outside the repository in owner-only files. This report contains aggregate counts only.

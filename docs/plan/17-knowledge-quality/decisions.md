# Slice 17 decisions

Cites the [knowledge-quality rules](../../wiki/knowledge-quality.md), [taxonomy quality ticket](../10-knowledge-taxonomy/ticket-04-quality.md), [private review schema](../../../raw/v1/journal-reviewed-manifest.schema.json), and [review scoring contract](../../../raw/v1/journal-reviewed-eval-lib.mjs).

## D68: owner review and evaluation review stay distinct

Owner approval determines current personal knowledge. Independent annotation establishes a reference answer for measuring a model. The same model output cannot create its own reference answer, and an evaluation correction cannot silently publish or retract an owner item. Preserve both histories and their provenance.

## D69: draft material is not a qualification corpus

The inspected private drafts have pending labels and privacy review. The checked-in balanced classifier fixture has no independent review or split history. They may seed a private review queue, but their presence does not satisfy corpus readiness. The pruned branch is marking a model pass because a fixture or owner feedback exists.

## D70: branch-specific, family-disjoint evaluation

Journal, assertion, and concept or relation cases have separate denominators, thresholds, and verdicts. Whole semantic families stay in one partition, and a locked holdout is single-use for each frozen candidate. The pruned branch is pooling branches or tuning against holdout examples.

## D71: freeze the gate before inference

The initial policy requires at least 10 independently reviewed positive examples for each journal kind and each assertion outcome, at least 30 reviewed empty or negative sources per branch, at least 20 reviewed concept or relation proposals including 10 negative relation cases, and representation of every declared difficult pattern. A qualification corpus must also include at least 10 randomly sampled production sources per branch to expose selection bias. These are minimum coverage requirements, not proof of statistical certainty.

For a frozen candidate, require exact excerpt validity of 100%, zero cross-owner disclosure, zero unsupported skill or profile additions on the declared negative challenge cases, zero wrong published relations, journal entry precision at least 0.90 and recall at least 0.80, assertion macro F1 at least 0.85 with each outcome F1 at least 0.75, concept precision at least 0.90 and recall at least 0.80, and relation precision at least 0.95 and recall at least 0.80. Any undefined required rate, runtime failure on an evaluated case, or missing coverage fails readiness or qualification. These are predeclared v2 policy values to review before the first real run; changing them creates a new policy version, never a reinterpretation of a completed result.

## D72: private evidence and public aggregates have different boundaries

Private cases, predictions, disagreement notes, and row-level outputs remain in a restricted workspace outside the repository. A public report may contain only disclosure-reviewed aggregate coverage, digests of safe configuration, and verdicts. An evaluated but unapproved disagreement is not silently relabeled. The pruned branch is committing transcript-bearing cases or treating an aggregate report as sufficient to reconstruct individual decisions.

## D73: independent gates and rollback

Model promotion requires a frozen baseline comparison, branch-specific safety gates, a single locked-holdout decision, and a retained rollback configuration. Private graph cutover and external deployment keep their own verdicts. A failed, unavailable, or inconclusive model run leaves the previous candidate in use and the quality gate open.

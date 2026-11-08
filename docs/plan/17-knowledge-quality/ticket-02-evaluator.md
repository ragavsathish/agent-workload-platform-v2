# Ticket 02: frozen, branch-specific evaluation

Cites the [slice plan](plan.md), [review scoring contract](../../../raw/v1/journal-reviewed-eval-lib.mjs), and [taxonomy quality ticket](../10-knowledge-taxonomy/ticket-04-quality.md).

Freeze baseline and candidate identities, review snapshot, partition digests, category version, scoring rules, threshold policy, resource budget, and rollback target before inference. Run the actual journal, assertion, and concept or relation proposal boundaries on their own reviewed partitions. Score exact evidence separately from semantic meaning, distinguish missing and wrong predictions, and report every denominator and runtime error. Measure repeated-run disagreement on a declared development subset. Machine-assisted similarity may flag a case for review but cannot settle its label.

The evaluator writes row-level predictions and disagreements only to a private destination. A public summary has approved aggregate counts and no examples, identifiers, paths, or source-derived hashes. Scorer and branch versions are recorded so a later run is comparable to its baseline.

Acceptance: [quality-004](spec.feature), [quality-005](spec.feature), and [quality-006](spec.feature).

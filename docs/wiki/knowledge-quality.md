# Reviewed quality for owner knowledge

Cites the [knowledge taxonomy](knowledge-taxonomy.md), [knowledge continuity](knowledge-extraction-migration.md), [private review schema](../../raw/v1/journal-reviewed-manifest.schema.json), [review scoring contract](../../raw/v1/journal-reviewed-eval-lib.mjs), and [slice 17 decisions](../plan/17-knowledge-quality/decisions.md).

## A proposal, a label, and a published item are different things

An owner source may yield a journal entry, an assertion classification, a subject concept, or a relation proposal. The source and every model proposal retain their own identities. A human reviewer can approve, correct, reject, or mark a missed item. The reviewed label is evidence for evaluation; a model prediction is never its own reference answer. Publication still requires the owner's separate review decision. An absent feedback action is pending, not approval.

Quality is measured separately for journal extraction, assertion classification, and concept or relation proposal. A result in one branch cannot compensate for an error in another. Exact source quotation proves only that words were present; it does not prove the proposed meaning, category, or relationship. Quoted speech, questions, commands, negation, ambiguity, and third-party claims need explicit negative or corrected labels.

## Private review corpus

The private corpus retains source text, exact evidence anchors, expected items or an explicit empty answer, review actions, reviewer and adjudication history, source kind, error slice, and semantic-family identity. It also records which examples were sampled randomly and which were selected because of a suspected error. A second reviewer resolves disputed labels before qualification. Owner decisions about current knowledge remain authoritative even if an evaluation label later changes.

No private source, label, example identifier, private path, or individual result enters a public report. A published aggregate can state coverage counts and metrics only after disclosure review. Every source-derived evaluation file stays outside the repository in a private workspace with restricted access. The old drafts are input candidates for review, never automatically approved labels.

Whole semantic families, including near duplicates and paraphrases, belong to one partition. Development supports changes; challenge tests declared difficult patterns; a locked holdout is opened once for a frozen promotion candidate; production shadow monitors drift after independent review. If holdout content is inspected for diagnosis, that version is retired. A later corpus version can supersede an earlier one without rewriting its historical report.

## Qualification decision

Before a candidate is run, freeze the baseline, model and prompt identities, taxonomy version, review snapshot, corpus partition digests, scoring rules, thresholds, resource limits, and rollback target. The review ledger must prove independent approval, privacy review, family separation, exact evidence validity, positive and empty cases, and risk-slice coverage. No gate passes on a draft, synthetic fixture, self-review, or a handful of smoke examples.

Score each branch on its own expected objects: false durable additions, misses, wrong type or category, unsupported interpretation, exact excerpt failures, wrong or missing relations, and runtime failure. Report confusion by kind, negative-case behavior, uncertainty or disagreement, repeat-run variation, latency, and the observed denominator for every rate. A missing denominator or incomplete branch remains open. The owner can inspect a private disagreement report and decide a correction; that changes a future dataset version, not the frozen result being judged.

The reviewed quality gate and old-data migration gate are independent. A model-quality pass alone cannot authorize private-knowledge cutover, and a successful graph replay cannot prove semantic quality. The [slice plan](../plan/17-knowledge-quality/plan.md) specifies the first measurable gate.

# Ticket 04: model quality and gate

Cites [slice plan](plan.md), [taxonomy rules](../../wiki/knowledge-taxonomy.md), [source extraction evaluation](../../../raw/v1/journal-extraction-eval.mjs), and [prior quality gate](../08-knowledge-extraction-migration/ticket-04-quality-gates.md).

Prepare a privacy-safe, human-reviewed corpus with explicit journal, topic, skill, profile, no-assertion, quoted speech, command, ambiguity, correction, and relation cases. Before running inference, record model, prompt, category version, corpus digest, scoring rubric, and numeric thresholds. Report per-kind confusion and total false additions, misses, exact excerpt failures, semantically unsupported labels, wrong categories, and wrong relations. Review disagreements independently. Keep gate open when cases, thresholds, or live model access are missing. Keep migration reconciliation and deployment verdicts independent.

Acceptance: [taxonomy-010](spec.feature) and [taxonomy-011](spec.feature).

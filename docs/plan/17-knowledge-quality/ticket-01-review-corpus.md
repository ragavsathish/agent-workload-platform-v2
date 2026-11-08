# Ticket 01: private review corpus and readiness

Cites the [slice plan](plan.md), [quality rules](../../wiki/knowledge-quality.md), and [private review schema](../../../raw/v1/journal-reviewed-manifest.schema.json).

Create a private candidate inventory from eligible owner sources and prior feedback. Distinguish proposed items, owner decisions, independent labels, and explicit misses. Keep the original observation and every correction. Require an independent review action, an adjudication record when reviewers disagree, privacy approval, and exact source anchors before a case is eligible. Drafts remain pending.

Cluster paraphrases and near duplicates into semantic families before assigning development, challenge, locked holdout, or production shadow. Check both family and normalized-content overlap. Record random and error-targeted sample counts and the fixed difficult-pattern coverage. Store private records outside the repository; emit a content-free readiness summary. Never expose a locked-holdout case for diagnosis without retiring that version.

Acceptance: [quality-001](spec.feature), [quality-002](spec.feature), and [quality-003](spec.feature).

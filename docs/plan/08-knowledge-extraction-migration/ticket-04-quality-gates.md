# Ticket 04: live qualification gates

Cites [slice plan](plan.md), [source extraction evaluation](../../../raw/v1/journal-extraction-eval.mjs), [journal prompt](../../../raw/v1/journal-prompt.md), [source processor](../../../raw/v1/knowledge-processor.mjs), and [knowledge rules](../../wiki/knowledge-extraction-migration.md).

Create a reviewed privacy-safe corpus with decisions, goals, learning, questions, commands, reported speech, ambiguous profile claims, low confidence, and false evidence. Pin the model, prompts, classifier, threshold, and corpus version. Report false additions, missed durable statements, exact excerpt accuracy, and category errors by mode; review thresholds before declaring a pass. Separately run a real authorized snapshot migration report and rollback rehearsal. A synthetic corpus or preview-only inventory does not satisfy the migration gate. Start with the named qualification scenarios in the [feature](spec.feature).

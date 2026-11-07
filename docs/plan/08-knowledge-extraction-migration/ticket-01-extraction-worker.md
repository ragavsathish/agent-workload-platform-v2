# Ticket 01: extraction worker and result incorporation

Cites [slice plan](plan.md), [source processor](../../../raw/v1/knowledge-processor.mjs), [source task runner](../../../raw/v1/knowledge-task-runner.ts), [journal prompt](../../../raw/v1/journal-prompt.md), [candidate prompt](../../../raw/v1/candidate-prompt.md), and [daily prompt](../../../raw/v1/daily-prompt.md).

Build the three bounded processing modes. Validate inputs and outputs, keep source excerpts exact, record model and processing versions, and make filtered/no-candidate outcomes visible. Ingest a result only when task, owner, source, mode, and request identity match. Preserve a result digest and refuse conflicting replay. Journal and classification may fail independently; daily prose must match its exact source bundle. Start with the named extraction scenarios in the [feature](spec.feature), record red then green evidence, and use synthetic text.

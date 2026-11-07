# Slice 08 decisions

Cites [source processor](../../../raw/v1/knowledge-processor.mjs), [source task runner](../../../raw/v1/knowledge-task-runner.ts), [source graph routes](../../../raw/v1/gateway-main.py), [source migration query](../../../raw/v1/knowledge-projection-migration.ru), and [slice 07 decisions](../07-personal-knowledge/decisions.md).

## D30: results are acknowledged after incorporation

A task completion and a knowledge write are separate facts. Match the task, owner, source, mode, and request version before accepting output. Store a result identity and digest; same-content replay is harmless, conflict is a visible error. A processor or writer outage remains mode-specific and retryable. An empty filtered, no-assertion, or low-confidence outcome is a recorded result, not a silently missing suggestion.

## D31: evidence and review retain authority

Exact source excerpts and processing metadata accompany every suggestion. The owner decides whether a candidate becomes accepted knowledge. Preserve all prior review actions, original output, corrected interpretation, and source references. A graph view is rebuildable from the authoritative history and cannot grant review authority.

## D32: import is a separate lineage

Import a frozen snapshot with immutable snapshot identity, resource mapping, and checkpoints. Do not silently regenerate old suggestions from old transcripts. Preserve the legacy resource identifier and its original provenance. Ambiguous ownership, missing sources, contradictory review records, and unsupported graph shapes go to quarantine with a reason. The import can resume and replay without duplicate actions.

## D33: reconciliation precedes cutover

Compare each source collection and target interpretation, not only total triples. Verify source messages, entries, decisions, candidates, action order, accepted facts, daily pages, and quarantines. Owner-visible sampled reads must agree. Keep the source snapshot and a reversible read switch until rollback rehearsal succeeds. Never erase the original graph as part of cutover.

## D34: quality and data gates remain independent

The live inference gate tests a reviewed corpus, including negative and ambiguous statements, under pinned model and prompt versions with measured precision, recall, attribution, and category errors. The old-data gate compares a real authorized snapshot against its import. Passing one cannot waive the other. Thresholds and any exceptions require explicit review before live cutover.

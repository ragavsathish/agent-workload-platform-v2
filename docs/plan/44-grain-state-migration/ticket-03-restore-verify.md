# Ticket 03: restore, compare, and preserve the native rollback source

## Acceptance

- Apply only the cold-verified snapshot to the existing retained Grain candidate PV.
- Verify copied state before Grain starts; expected tenant/key identities and database counts must match the snapshot.
- Start the candidate through the reviewed Semaphore template with the configured real owner identity and private candidate token; wait for GET `/health`.
- Query task list, session catalog, routing, latest typed turn, and deployment status through read-only operations; report only counts/statuses/hashes.
- Preserve session and deployment IDs by stable hash; no transcripts, titles, event JSON, or credentials enter task output.
- Restart the native v2 stack from the unchanged source through Semaphore and prove the source `/health` and child processes are ready.
- Keep both runtimes on separate directories; no two processes may attach to the same SQLite or LMDB stores.
- On failure, restart the native stack from source and leave the candidate detached. Do not overwrite or delete the rollback snapshot.
- No v1 service stops and no owner/public route changes occur in this slice.

## Evidence

Record pre/post snapshot count summaries, store integrity results, identity hashes, PV/PVC names/UID, candidate and native health, task logs with secret scans, downtime duration, rollback state, and unchanged v1 inventory.

Completed by Semaphore task `2147483569` on pin `29d1c32955347e0534dbb80acea9a9a8b1d6a4f1`. Qualification tasks `2147483572`, `2147483571`, and `2147483570` passed before migration; post-migration candidate owner `2147483568`, native preflight `2147483567`, and layout check `2147483566` also passed. Snapshot `/Users/sathish.narayanan/Library/Application Support/AWPv2/migrations/grain-state-slice44-2026-11-09-task-2147483569` has 48 manifest entries (SHA-256 `065aec886d880f5e5cab52559561546e8eb5d40c5fbacf6230109f6f794ca4f5`). A read-only verification confirmed all manifest file hashes/sizes and private file/directory modes. The cold snapshot had 32 session events and 66 deployment events; the other four SQLite stores had zero events. The candidate preserved all source event IDs and tenant hashes across six SQLite stores. Candidate startup added four deployment observations. Candidate owner query and `/health` passed, with one active session and zero tasks; the native service restart and final preflight passed with the same session identity hash. The existing PVC UID and PV name matched before and after migration. The `candidate-before` rollback snapshot preserves the prior canonical layout and its checksums passed; no rescue was needed. v1 remained running.

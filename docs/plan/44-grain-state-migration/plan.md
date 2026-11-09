# Slice 44: migrate native Grain state into durable k3d storage

This slice advances [the k3d operations specification](../39-k3d-semaphore-operations/plan.md) and [the Grain candidate stage](../43-k3d-grain-core/plan.md). It follows the SQLite [Online Backup API](https://sqlite.org/backup.html) and LMDB [`mdb_env_copy2`](https://github.com/LMDB/lmdb/blob/mdb.master3/libraries/liblmdb/lmdb.h) backup contract.

## Goal

Copy the current non-empty native AWP v2 Grain state into the stable k3d Grain volume without losing owner sessions, task state, deployment state, or Grain read models. Keep the native source intact and recoverable. v1 remains running, and this slice does not enable an owner route or declare cutover readiness.

## Boundary

The source is the live native Grain tree at `/private/tmp/awp-v2-state`. Its SQLite event stores include 52 deployment events and 16 session events; the other SQLite domain stores currently have no events. LMDB read-model environments exist for sessions, tasking, deployment, knowledge, song, and incidents. The Grain Java process owns these LMDB environments and SQLite connections. A flat `cp` of live SQLite/WAL files or LMDB `data.mdb` is not an approved snapshot method.

Semaphore's native macOS runner performs the runtime refresh, quiescence, snapshot, copy, restart, and verification tasks. The runtime refresh validates the active content-addressed supervisor, starts the corrected runtime, and retains a narrowly scoped start fallback for the pinned previous runtime. The repository revision and playbook are pinned for each run. Ansible targets `localhost` on the macOS host. The playbook uses the reviewed `stack.py stop` and `stack.py start` lifecycle commands; it does not send process signals directly, kill individual processes, or modify v1 LaunchAgents/containers.

Preflight reads owner-scoped task/session state and process/store ownership. The configured owner currently has zero tasks, one selected session, no active typed turn, idle compaction, and no task attached. Any pending/running/sleeping task, uncertain compaction, newly active turn, unexpected process owner, or change to the source inventory aborts before quiescence.

The migration creates a timestamped rollback snapshot under `~/Library/Application Support/AWPv2/migrations/` with directory mode `0700` and private file modes. The existing `/private/tmp/awp-v2-state` source remains in place. After the supervisor has gracefully stopped every writer, the playbook copies only the configured Grain data trees and task artifacts/workspaces to the snapshot and the fixed host-backed k3d PV directory. Runtime PID files and logs, credentials, Pi/Gondolin state, model caches, and unrelated host data are excluded.

The destination uses the Grain StatefulSet in namespace `awp-v2` and `grain-data-stable` PVC bound to static PV `awp-v2-grain-candidate` with reclaim policy `Retain`. Its hostPath remains unchanged; the container mounts the `grain-candidate` subPath at `/private/tmp/awp-v2-state`, so Grain sees the same lexical store paths as native v2. Copy verification runs before starting the candidate. It checks SQLite integrity, row/tag/tenant counts and tenant identity hashes, plus LMDB file size and hashes, without printing event payloads. After starting Grain with the actual configured owner, session/task reads are checked by counts/status and sorted identity hash. Startup may append deployment observations; expected new rows are measured and excluded from the pre-start equivalence check.

After the cold snapshot and candidate verification, the original native v2 stack is started again through the same Semaphore task and checked for health. If any check fails, the task restarts the unchanged native source and leaves the candidate detached for diagnosis. Candidate data is never copied back over the source. v1 is untouched.

## Scenarios

See [feature scenarios](spec.feature).

## Tickets

- [Ticket 01: pin and qualify host lifecycle operations through Semaphore](ticket-01-semaphore-lifecycle.md)
- [Ticket 02: quiesce and take a durable, consistent Grain snapshot](ticket-02-snapshot.md)
- [Ticket 03: restore, compare, and preserve the native rollback source](ticket-03-restore-verify.md)

## Exit evidence

Record the exact Semaphore project/template/task and pinned source revision; preflight counts/statuses; source supervisor and child PIDs; downtime start/end; source and snapshot directory identities and modes; snapshot checksum manifest; per-store SQLite integrity and counts; LMDB identity/count hashes; destination PVC/PV identity; candidate owner query summary and `/health`; native stack restart and health; and confirmation that v1 remains unchanged. Do not include secrets, event payloads, transcripts, titles, owner identifiers, or raw database rows.

This slice completes only when the candidate is restored with equivalent pre-start state and the native stack is back. Gateway, Edge/Flutter, support services, host bridge, Prometheus/OTLP, new-v2 PromQL, owner journey, and full cutover remain later gates.

Migration completed through Semaphore task `2147483569` on pinned playbook revision `29d1c32955347e0534dbb80acea9a9a8b1d6a4f1`. Qualification tasks were syntax/dry-run `2147483572`, pre-migration layout `2147483571`, source preflight `2147483570`, candidate owner `2147483568`, final source preflight `2147483567`, and post-migration layout `2147483566`; all succeeded. The task-specific backup is `~/Library/Application Support/AWPv2/migrations/grain-state-slice44-2026-11-09-task-2147483569`; its manifest has 48 files and SHA-256 `065aec886d880f5e5cab52559561546e8eb5d40c5fbacf6230109f6f794ca4f5`. A read-only verification confirmed every manifest size/hash and all private directory/file modes. The cold source snapshot contained 32 session events (16 tags) and 66 deployment events (33 tags); each of the other four SQLite stores had zero events. The candidate contains 32 session events and 70 deployment events after four startup observations; all source event IDs and tenant hashes match across the six stores. Six LMDB `data.mdb` files were copied and size/hash verified before start. Candidate owner query and health passed, with one active session and zero tasks. The native service was restarted and its read-only health/preflight passed with the same selected-session identity hash. Both the PVC UID and PV name remained stable. The preserved `candidate-before` snapshot records the previous canonical candidate layout and passed the same manifest verification; the successful task did not invoke rescue. v1 was not changed.

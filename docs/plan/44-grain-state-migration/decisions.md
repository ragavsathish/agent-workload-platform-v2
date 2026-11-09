# Decisions

## D01 — Quiesce before the cross-store snapshot

SQLite's Online Backup API can produce a consistent snapshot of one live database, and LMDB exposes a consistent environment copy operation. The AWP state spans multiple SQLite and LMDB stores that must represent one service state. A short, verified quiescence avoids cross-store skew and makes file-level cold copies safe. Use SQLite's backup API when possible; otherwise copy the whole SQLite DB and any WAL/SHM sidecars only after all writers have exited. Copy LMDB directories only while the owning process is stopped.

The candidate and rollback copy are separate; the source is not edited. The migration task must fail closed if any active task, turn, compaction, unexpected process, non-empty task output, or source path change appears.

## D02 — Semaphore owns the host lifecycle

The local native runner is the authorized path for host service stop/start and migration. The task is tied to a pinned repository, a reviewed local inventory, an exact stack state path, and a fixed runner tag. No raw shell signal, broad process match, network appliance connection, or generic host command is allowed.

## D03 — Keep the native source as rollback

The active source under `/private/tmp/awp-v2-state` remains intact after migration. Create a second timestamped copy in durable private storage before the candidate copy. On any failure, start the native stack from the original state and leave the candidate data for diagnosis. Do not roll a k3d copy back over the native state.

## D04 — Stage state without switching owner traffic

The copied candidate can be queried and verified inside the local cluster, but it is not an owner route or cutover. v1 remains up until the k3d app plane, host bridge, support services, telemetry, PromQL, owner/task continuity, and rollback all pass.

## D05 — Preserve Grain's path-derived tenant identity

Grain derives tenant identity from each configured storage-directory path. Copying SQLite and LMDB bytes while changing those paths creates a different tenant namespace, even when the files and event IDs are present. Mount the existing retained PV's `grain-candidate` subPath at `/private/tmp/awp-v2-state` and configure each store to the same lexical path used by native v2. Keep the PV/PVC identity unchanged. The first migration rescue retains the prior canonical manifest and snapshot; after successful migration, the native-path manifest is the candidate's running layout.

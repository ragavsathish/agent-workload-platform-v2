# Ticket 02: quiesce and take a durable, consistent Grain snapshot

## Acceptance

- The read-only preflight is rerun immediately before the stop and aborts if task/session/compaction state is active or ambiguous.
- A reviewed Semaphore template stops only the verified native v2 supervisor; the process tree proves all Grain, gateway, Pi, broker, and scheduler children exited cleanly.
- The original source remains untouched and its root is mode `0700`.
- Snapshot and candidate paths are separate, outside the checkout, under `~/Library/Application Support/AWPv2`; directories are mode `0700`, regular files mode `0600`.
- Copy all SQLite DBs with the SQLite Online Backup API or, while fully quiesced, cold-copy the complete database plus sidecars. Copy each LMDB environment as a complete quiesced directory, including required map data; no live `data.mdb` copy.
- Preserve the `sessions`, `sessions-deployment`, `sessions-knowledge`, `sessions-song`, `sessions-incidents`, and `tasking` stores, plus declared task artifact/workspace data.
- Produce a manifest of relative paths, sizes, and SHA-256 hashes without opening or printing payloads.
- Verify every SQLite database with `PRAGMA integrity_check` and compare counts/tags/tenants; verify LMDB open/query behavior and key identity hashes.

## Evidence

Record exact source, backup, and candidate paths (redacted to approved local path only), modes, store inventory, checksum manifest digest, integrity result, and the source process shutdown window.

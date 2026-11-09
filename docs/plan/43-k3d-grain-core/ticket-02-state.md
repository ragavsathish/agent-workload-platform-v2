# Ticket 02: persist isolated candidate Grain state

## Acceptance

- Mount Grain storage from the dedicated k3d cluster's existing host-backed local-path storage root.
- Use a candidate-only directory separate from `/private/tmp/awp-v2-state`.
- Restart the Grain pod and show the same PVC identity and mounted directory.
- Inspect native SQLite databases read-only, reporting counts only; never copy open SQLite/LMDB state.
- Document snapshot/replay migration as a required cutover gate when native event stores are non-empty.

## Evidence

Record PVC name/UID, storage path, pod restart result, health response, and safe native-store inventory summary. Do not include event payloads or credentials.

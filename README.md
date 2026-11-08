# Agent Workload Platform v2

AWP v2 rebuilds the current product one documented slice at a time. The [Löyly way of working](docs/wow/way-of-working.md) keeps pinned v1 evidence in `raw/`, stack-free behavior in the [domain wiki](docs/wiki/index.md) and [slice plans](docs/plan/15-local-release-readiness/plan.md), and build choices under `impl/`.

Slices 01–13 have implementation covering tasking, conversation, voice, knowledge, song, taxonomy, incidents, and task product delivery. Slice 14 external owner qualification remains open. Slice 15 [supervised local release readiness](docs/plan/15-local-release-readiness/plan.md) has a [local evidence report](impl/qualification/local-release-report.md): eight owner journeys were exercised; its song journey remained unqualified because its manifest was supplied directly. Slice 16 [qualified the real durable lyric task through private audio](impl/qualification/durable-song-report.md) in a fresh supervised state. The incident journey used an isolated managed-alert fixture. These are local results, not external deployment parity.

## Read order

1. [Method](docs/wow/way-of-working.md) and [invariants](docs/wow/invariants.md)
2. [Domain wiki](docs/wiki/index.md)
3. [Current slice](docs/plan/16-durable-song-owner/plan.md) and [scenarios](docs/plan/16-durable-song-owner/spec.feature)
4. [Pinned v1 evidence](raw/v1/manifest.md) when checking a claim

Run `harness/verify.sh` for implementation and documentation gates. The slice 15 owner journey checks are opt-in and read a private evidence ledger via `AWP_LOCAL_RELEASE_REPORT`. The focused `release-005` check passes against the fresh slice 16 ledger and still fails against the historical slice 15 ledger. Run `harness/fence.sh /path/to/new-room` for a clean-room regeneration checkout.

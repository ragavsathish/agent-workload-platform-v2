# Agent Workload Platform v2

AWP v2 rebuilds the current product one documented slice at a time. The [Löyly way of working](docs/wow/way-of-working.md) keeps pinned v1 evidence in `raw/`, stack-free behavior in the [domain wiki](docs/wiki/index.md) and [slice plans](docs/plan/15-local-release-readiness/plan.md), and build choices under `impl/`.

Slices 01–13 have implementation covering tasking, conversation, voice, knowledge, song, taxonomy, incidents, and task product delivery. Slice 14 external owner qualification remains open. Slice 15 [supervised local release readiness](docs/plan/15-local-release-readiness/plan.md) has a [local evidence report](impl/qualification/local-release-report.md): eight owner journeys were exercised, while the complete song journey remains unqualified because lyric generation lacked its durable Pi owner endpoint. The incident journey used an isolated managed-alert fixture. This is a local candidate, not external deployment parity.

## Read order

1. [Method](docs/wow/way-of-working.md) and [invariants](docs/wow/invariants.md)
2. [Domain wiki](docs/wiki/index.md)
3. [Current slice](docs/plan/15-local-release-readiness/plan.md) and [scenarios](docs/plan/15-local-release-readiness/spec.feature)
4. [Pinned v1 evidence](raw/v1/manifest.md) when checking a claim

Run `harness/verify.sh` for implementation and documentation gates. The slice 15 owner journey checks are opt-in and read a private evidence ledger via `AWP_LOCAL_RELEASE_REPORT`; the unqualified song scenario intentionally fails. Run `harness/fence.sh /path/to/new-room` for a clean-room regeneration checkout.

# Agent Workload Platform v2

AWP v2 rebuilds the current product one documented slice at a time. The [Löyly way of working](docs/wow/way-of-working.md) keeps pinned v1 evidence in `raw/`, stack-free behavior in the [domain wiki](docs/wiki/index.md) and [slice plans](docs/plan/09-personal-song/plan.md), and build choices under `impl/`.

Slices 01–06 cover task admission, task lifecycle, persistent conversation, live voice, deployed voice journey, and public owner deployment. The external tailnet owner route remains unqualified; see the [slice 06 plan](docs/plan/06-public-deployment/plan.md). Slice 07 [personal knowledge](docs/plan/07-personal-knowledge/plan.md) and slice 08 [knowledge extraction and migration](docs/plan/08-knowledge-extraction-migration/plan.md) have implementation, while live quality and old-data cutover remain gated. Slice 09 [personal song](docs/plan/09-personal-song/plan.md) is planned for lyric review, durable approval, rendering, private audio, and cancellation. Its [behavioral model](specs/personal-song.allium) and [implementation notes](impl/personal-song-notes.md) are ready for the build invocation.

## Read order

1. [Method](docs/wow/way-of-working.md) and [invariants](docs/wow/invariants.md)
2. [Domain wiki](docs/wiki/index.md)
3. [Current slice](docs/plan/09-personal-song/plan.md) and [scenarios](docs/plan/09-personal-song/spec.feature)
4. [Pinned v1 evidence](raw/v1/manifest.md) when checking a claim

Run `harness/verify.sh` for the implemented slices and documentation gate. The slice 09 scenarios enter the acceptance harness during its build. Run `harness/fence.sh /path/to/new-room` for a clean-room regeneration checkout.

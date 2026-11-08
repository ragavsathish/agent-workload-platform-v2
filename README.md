# Agent Workload Platform v2

AWP v2 rebuilds the current product one documented slice at a time. The [Löyly way of working](docs/wow/way-of-working.md) keeps pinned v1 evidence in `raw/`, stack-free behavior in the [domain wiki](docs/wiki/index.md) and [slice plans](docs/plan/24-owner-client/plan.md), and build choices under `impl/`.

Slices 01–16 implement tasking, session CQRS, live voice, personal knowledge, song, taxonomy, incidents, artifact delivery, worker delivery, and local release qualification. Slices 17–22 add a private knowledge-quality gate, Grain-backed ORC relations, a private owner review workbench, and an isolated public v2 candidate. Slice 23 adds voice, durable, and full deployment levels. Slice 24 specifies the missing owner client surface against the Grain session and task contracts. The [full local verifier](harness/verify.sh) covers the Grain event models, Allium contracts, acceptance suites, runtime builds, and deployment probes.

## Current qualification status

- [Knowledge quality](impl/qualification/knowledge-quality-report.md): the 120 private classifier and journal candidates remain pending; zero are eligible for a semantic quality run. Independent review, privacy approval, and coverage evidence are still needed.
- [Knowledge continuity](impl/qualification/private-knowledge-continuity-report.md): the shadow rehearsal reconciled 413 mapped resources; 12 unlinked metadata records remain quarantined. No live read switch or owner approval is recorded.
- [ORC relations](impl/qualification/relation-model-reliability-report.md): synthetic model-to-Grain proposals are staged pending owner review. The Codex experiment is synthetic protocol evidence; it does not qualify semantic accuracy.
- [Public candidate](impl/qualification/public-candidate-report.md): prepared-only. No authenticated v2 public route or separate-device owner journey has been qualified.
- [PR soundtrack](docs/plan/14-pr-soundtrack/plan.md): the procedure remains manual; real pull request publication and reviewer access have not been qualified.

Local acceptance evidence is not external deployment parity. Keep the v1 service and owner reads authoritative until the applicable private and public gates have been explicitly reviewed and approved.

## Read order

1. [Method](docs/wow/way-of-working.md) and [invariants](docs/wow/invariants.md)
2. [Domain wiki](docs/wiki/index.md)
3. [Latest slice](docs/plan/24-owner-client/plan.md) and [scenarios](docs/plan/24-owner-client/spec.feature)
4. [Pinned v1 evidence](raw/v1/manifest.md) when checking a claim
5. Product implementation and qualification evidence under `impl/`

Run `harness/verify.sh` for implementation and documentation gates. Run `harness/fence.sh /path/to/new-room` to repeat verification from a clean regeneration checkout.

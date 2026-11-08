# Domain wiki

Cites [the pinned v1 source inventory](../../raw/v1/manifest.md).

- [Task request and agent task](tasking.md) defines the admission vocabulary and
  rules used by [slice 01](../plan/01-task-admission/plan.md).
- [Task lifecycle and execution](task-lifecycle.md) defines progress, outcome,
  interruption, and capability rules used by [slice 02](../plan/02-task-lifecycle/plan.md).
- [Current conversation orchestration](session-orchestration.md) defines active
  selection, turn routing, and public history used by [slice 03](../plan/03-session-orchestration/plan.md).
- [Live owner conversation](live-session-integration.md) defines the public
  route, voice connection, and production turn behavior used by [slice 04](../plan/04-live-session-integration/plan.md).
- [Deployed owner voice journey](deployed-voice-journey.md) defines the spoken
  media and qualification boundary used by [slice 05](../plan/05-deployed-voice-journey/plan.md).

- [Public owner deployment](public-deployment.md) defines authenticated entry, external media, service recovery, and scheduling for [slice 06](../plan/06-public-deployment/plan.md).
- [Personal knowledge](personal-knowledge.md) defines completed-turn intake, reviewable knowledge, and explicit daily accounts for [slice 07](../plan/07-personal-knowledge/plan.md).

- [Knowledge extraction and continuity](knowledge-extraction-migration.md) defines result incorporation, projection repair, and old-data migration for [slice 08](../plan/08-knowledge-extraction-migration/plan.md).
- [Personal song](personal-song.md) defines lyric review, approval, rendering, private audio, and cancellation for [slice 09](../plan/09-personal-song/plan.md).
- [Personal knowledge taxonomy](knowledge-taxonomy.md) defines category hierarchy, owner-reviewed relations, model proposals, and quality qualification for [slice 10](../plan/10-knowledge-taxonomy/plan.md).
- [Grounded relation proposals](knowledge-relations.md) defines owner-reviewed hierarchy suggestions and the boundary from factual claims for [slice 19](../plan/19-grounded-relations/plan.md).
- [Relation proposal reliability](knowledge-relations.md#failed-attempt-and-retry) defines failed attempts, explicit retries, and the path qualification boundary for [slice 20](../plan/20-relation-model-reliability/plan.md).
- [Private owner review workbench](private-review-workbench.md) defines explicit review of quality cases and old metadata with separate gates for [slice 21](../plan/21-private-review-workbench/plan.md).
- [Operational incidents](operational-incidents.md) defines managed alert epochs, bounded investigations, and owner review for [slice 11](../plan/11-operational-incidents/plan.md).
- [Task result delivery](task-result-delivery.md) defines bounded answer presentation and owner-scoped patch or scratch workspace retrieval for [slice 12](../plan/12-task-result-delivery/plan.md).
- [Pull request soundtrack](pr-soundtrack.md) defines the manual reviewer publication and qualification boundary for [slice 14](../plan/14-pr-soundtrack/plan.md).
- [Supervised local release readiness](local-release-readiness.md) defines the evidence boundary for a real local owner journey across current services in [slice 15](../plan/15-local-release-readiness/plan.md).
- [Owner workspace](owner-client.md) defines the owner-facing conversation, task, and presentation behaviors for [slice 24](../plan/24-owner-client/plan.md).
- [Task routing](task-routing.md) defines owner-authorized attachment, turn routing, reconciliation, and restart behavior for [slice 25](../plan/25-task-routing-integration/plan.md).

The wiki records source-backed current behavior separately from agreed behavior
and explicit v2 decisions. It does not describe the implementation.

# Slice 10: personal knowledge taxonomy and qualified model path

Cites [taxonomy rules](../../wiki/knowledge-taxonomy.md), [review rules](../../wiki/personal-knowledge.md), [continuity rules](../../wiki/knowledge-extraction-migration.md), [source vocabulary](../../../raw/v1/knowledge-ontology.ttl), [source categories](../../../raw/v1/knowledge-processor.mjs), and [decisions](decisions.md).

## Goal and boundary

Give owner knowledge an explicit, versioned category hierarchy and owner-reviewed subject relations. Let bounded model proposals enter the same review authority as other suggestions, then qualify their quality on a reviewed corpus. The [scenarios](spec.feature) define observable behavior from fresh state.

The slice begins with a category version, a settled owner source, or an existing reviewed item. It ends with a private proposal, reviewed publication, corrected/revoked interpretation, scoped query, or a measured qualification verdict. The authoritative review history from [slice 07](../07-personal-knowledge/plan.md) and result identity from [slice 08](../08-knowledge-extraction-migration/plan.md) are reused. The old graph migration and external deployment gates remain separate.

## Tickets

- [Ticket 01](ticket-01-taxonomy.md): define category identities, hierarchy, versions, and safe mapping of existing items.
- [Ticket 02](ticket-02-proposals.md): bounded model proposal, exact evidence, pending review, relation decisions, and replay/conflict behavior.
- [Ticket 03](ticket-03-projection.md): scoped, rebuildable category and relation reads; revocation and owner isolation.
- [Ticket 04](ticket-04-quality.md): reviewed corpus, precommitted thresholds, measured model quality and explicit gate verdict.

## Exit evidence

For each ticket, record named failing acceptance scenarios before building, then passing scenarios and the independent gate. Include a restart/rebuild comparison and a read attempted by a second owner. Record a live-model quality report with corpus digest, model, prompt, taxonomy version, per-category confusion, exact evidence validity, unsupported additions, misses, and wrong relations. Without reviewed cases and precommitted thresholds, report the quality gate as open. Implementation choices and operational prerequisites belong in [implementation notes](../../../impl/knowledge-taxonomy-notes.md).

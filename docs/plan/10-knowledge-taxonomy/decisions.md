# Slice 10 decisions

Cites [taxonomy rules](../../wiki/knowledge-taxonomy.md), [source categories](../../../raw/v1/knowledge-processor.mjs), [source shapes](../../../raw/v1/knowledge-shapes.ttl), and [slice 08 decisions](../08-knowledge-extraction-migration/decisions.md).

## D38: AWP owns its personal taxonomy

Use the journal and assertion kinds observed in the source as version-one categories. Add an explicit hierarchy and definitions so a category can be interpreted and changed deliberately. Treat no-assertion as an outcome, topic as a retrieval subject, and profile field as a subtype of explicit profile update. Do not map the external component's failure/success/problem seed classes onto owner knowledge. Future category changes create a version and migration decision rather than silently recategorizing history.

## D39: model output is a proposed interpretation

An exact quote, source identity, taxonomy version, and pinned model/prompt identify a proposal. Validate quote containment and eligible category before recording it. The owner accepts, corrects, or rejects the proposal through the existing review authority. A proposed broader/related relation needs its own review of both endpoints. No proposal is published by a graph import, search result, or model success alone.

## D40: current reads obey review state

Private graphs are derived views. Rebuild them from authoritative source, result, and review records. Query by authenticated owner and intersect every returned item and relation with current accepted state. A revoked or corrected item must not reappear via semantic search, traversal, or direct graph lookup through the owner API. Keep previous versions and review actions for audit.

## D41: quality is a separate measured decision

Pin model, prompt, taxonomy, reviewed corpus, and thresholds before scoring. Score evidence validity separately from semantic correctness and category/relation errors. A synthetic protocol test or exact-substring check cannot stand in for reviewed quality. If no reviewed corpus or threshold exists, keep the model-quality gate open and do not declare live qualification.

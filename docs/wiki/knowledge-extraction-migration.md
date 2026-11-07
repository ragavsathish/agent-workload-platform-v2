# Knowledge extraction and continuity

Cites [completed-turn knowledge](personal-knowledge.md), [the source processor](../../raw/v1/knowledge-processor.mjs), [source writer and reads](../../raw/v1/gateway-main.py), [source graph migration](../../raw/v1/knowledge-projection-migration.ru), [source graph operations](../../raw/v1/knowledge-graph-ops.py), and [the source ontology](../../raw/v1/knowledge-ontology.ttl).

## From source turn to reviewable result

A settled owner turn is eligible for journal and classification work under separate task identities. Each worker outcome has a source turn, mode, task identity, attempt, and processing version. A successful task result may say that nothing was found. It may also contain source-backed suggestions. A failure in one mode leaves the other mode and the conversation answer intact. The result must be recorded before a task can be considered fully incorporated into knowledge. Repeating a result with the same identity and content has one effect; conflicting content under the same identity is refused and investigated.

A journal suggestion must be supported by an exact source excerpt and have a permitted kind. Routine commands, status text, quotations from others, and unsupported inferences are excluded. A classification decision distinguishes conversation topics, explicit skill evidence, explicit profile updates, and no assertion. A low-confidence or no-assertion decision remains inspectable without creating a candidate. A candidate must retain its label, exact evidence excerpt, classifier decision, confidence, model and processing version. Review remains the owner's authority; extraction never directly creates an accepted profile fact. These rules derive from the [source processor](../../raw/v1/knowledge-processor.mjs) and [review contract](personal-knowledge.md).

A daily account is an independently requested result over an exact, bounded set of owner messages on one local date. Its version records the source-set identity, date, time zone, title, prose, model and processing version. A later account for the same date with different sources is a distinct version. The [source writer](../../raw/v1/gateway-main.py) replaces a date page, so its current page alone cannot establish all historical versions.

## Existing knowledge and continuity

The legacy store contains source messages, journal entries, classification decisions and probabilities, pending candidates, review actions, accepted facts, and daily pages in separate named collections. Some entries carry typed concepts while older records need the [source projection migration](../../raw/v1/knowledge-projection-migration.ru). Current reads select from multiple collections, including profile and learning collections unrelated to turn extraction. A migration inventory must distinguish each resource and its owner, source, review history, and current interpretation before import. A resource without provable owner or source is quarantined, not assigned by guesswork.

A migration takes an immutable source snapshot, creates a manifest of counts and digests by collection, maps legacy identities to stable imported identities, and records a checkpoint. Replaying the same snapshot does not duplicate suggestions, actions, or accounts. Reconciliation compares source and target counts, identities, review sequences, accepted facts, and account versions, including quarantined and unsupported records. A discrepancy blocks cutover. The source remains recoverable until owner-visible reads and rollback are verified.

## Qualification gates

Deterministic contract checks prove parsing, source attribution, repeat handling, isolation, and projection repair. A separate live model gate uses a reviewed, privacy-safe corpus with positive and negative examples, pinned model and prompt versions, and measured false additions, missed durable statements, evidence accuracy, and category errors. A second, separate old-data gate uses a real authorized snapshot and reconciliation report. Neither gate is implied by synthetic tests or by the other gate. See [slice 08](../plan/08-knowledge-extraction-migration/plan.md).

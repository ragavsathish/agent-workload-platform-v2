# Grounded relations in owner knowledge

Cites the [taxonomy](knowledge-taxonomy.md), [quality rules](knowledge-quality.md), [owner review rules](personal-knowledge.md), [pinned relation capability](../../raw/orc/commands.clj), [pinned relation discovery](../../raw/orc/ontology-exploration.clj), and [v1 evidence vocabulary](../../raw/v1/knowledge-ontology.ttl).

## A relation is a separate claim

Two accepted owner concepts do not imply an edge. A relation proposal names two stable concept identities, the proposed direction and kind, the owner source, and an exact source excerpt that supports the relationship itself. Evidence merely mentioning both labels is insufficient. A model can suggest a relation; only a separate owner review decision can make it current. Accepted endpoints must still be current at review and read time. A source owned by someone else, a missing endpoint, a self-edge, or a broader cycle is refused.

`broader` means the left subject is a type or subset of the right subject. `related` is a non-hierarchical subject association supported by the source. These are subject vocabulary relations, not claims that a person caused, owns, works for, believes, or did something. Such factual personal relations require their own relation vocabulary and evidence rules in a later slice. The discovery source also names causal and enabling links; those are outside this slice and must not be silently mapped to `related` or `broader`. Direction is part of identity and review. A reversed broader edge needs a new proposal.

## Proposal, decision, and current graph

The bounded proposal attempt has a stable identity tied to owner, source digest, endpoint identities and revisions, model, prompt, and relation policy version. It returns zero or a small declared maximum of candidates. Each candidate has a stable identity, exact supporting excerpt, model trace, and source identity. A repeat of the same result has one effect; changed content under that identity conflicts. Timeout, malformed output, unsupported kind, and empty output have visible outcomes and no accepted edge.

Proposals enter pending review. The owner may accept, reject, or correct a proposed relation. A correction records the original, the corrected kind or direction and exact evidence, and a replacement proposal linked to the original; it does not rewrite the model output. Acceptance is a separate action at the current revision. Rejection, correction, and revocation remain in ordered history. Revocation or endpoint revocation removes an edge from all current owner reads, including traversal and export. A derived graph may retain historical records, but current reads intersect it with authoritative review state. Rebuild and restart must produce the same current result and provenance.

## Quality boundary

The relation branch uses independently reviewed source and endpoint-pair labels. It measures wrong edges, missing edges, wrong direction or kind, unsupported meaning despite an exact excerpt, invalid excerpt, empty and negative cases, and runtime failure. Its model, prompt, policy, source partition and scoring rules are frozen before a quality run. The [reviewed quality gate](knowledge-quality.md) remains open without eligible independent labels; synthetic protocol cases cannot qualify semantic quality. Publication decisions and evaluation labels are separate histories.

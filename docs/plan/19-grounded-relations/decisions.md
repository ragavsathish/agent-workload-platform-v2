# Slice 19 decisions

Cites [the slice plan](plan.md), [relation rules](../../wiki/knowledge-relations.md), [pinned relation discovery](../../../raw/orc/ontology-exploration.clj), and [pinned relation commands](../../../raw/orc/commands.clj).

## D79: subject taxonomy only

Accept `broader` and `related` as proposal kinds. A broader edge points from narrower left subject to broader right subject. The source discovery also suggests causal relations, but the current owner taxonomy has no factual predicate policy. Do not map a causal or personal assertion into a subject association. Defer factual personal relations until a separate vocabulary and review contract exist.

## D80: the relation excerpt must support the edge

Exact source containment is required but insufficient. A candidate whose excerpt names two concepts without asserting the relation remains pending for human judgment and counts as unsupported if the reviewed reference rejects it. Source digest and endpoint identity/revision are part of the attempt. The model receives only a bounded owned source and accepted endpoint labels and evidence.

## D81: model output never becomes an accepted edge

Model candidates are immutable and separately staged as pending relation proposals. The owner decision is the sole publication authority. A retry under one result identity must match the complete payload; changed content is a conflict. Failed or empty attempts record an outcome without an edge.

## D82: correction is a linked replacement

Keep the original model proposal and decision history. Correcting a pending proposal rejects it as corrected and creates a linked replacement pending proposal with the owner's revised direction, kind, or evidence; the replacement requires a separate acceptance. This avoids making the model's claim appear to have been its corrected claim.

## D83: current reads are authorized by current decisions

Project accepted relations only when both endpoint concepts remain accepted and owner-scoped. On revocation or correction, rebuild or filter every owner graph read from current review state. Historical graph records may remain for audit but cannot authorize a visible edge. Restart and replay must agree.

## D84: relation quality is a separate gate

The existing private quality run needs an actual relation proposal path and independently reviewed relation cases. Synthetic checks establish command behavior, not semantic quality. A relation branch with no eligible reviewed cases or no actual model invocation remains open and cannot be reported passed.

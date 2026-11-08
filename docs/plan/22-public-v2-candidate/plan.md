# Slice 22: isolated public v2 candidate

Cites [candidate rules](../../wiki/public-v2-candidate.md), [public deployment rules](../../wiki/public-deployment.md), [running host observation](../../../raw/v1/public-candidate-host-observation.md), [decisions](decisions.md), and [scenarios](spec.feature).

## Goal and boundary

Prepare and qualify an independently addressable v2 public candidate while
the current owner service continues running. The boundary begins with a
complete inventory of occupied resources and ends with a reviewable release
decision based on exact-owner authorization, a separate-device spoken journey,
foreign denial, restart continuity, and rollback. The candidate cannot borrow
the existing service's address, identity, state, or success evidence.

The [scenarios](spec.feature) are the acceptance contract. The [source host
observation](../../../raw/v1/public-candidate-host-observation.md) identifies
the current resource conflicts and external prerequisites. Specific values,
operational commands, and topology belong to the build record.

## Tickets

- [Ticket 01](ticket-01-isolated-candidate.md): inventory, unique candidate identity, preflight, and safe start; `candidate-001` through `candidate-003`.
- [Ticket 02](ticket-02-owner-and-media.md): exact owner entry, adversarial refusal, and live media; `candidate-004` through `candidate-007`.
- [Ticket 03](ticket-03-continuity-and-decision.md): restart, rollback, and evidence decision; `candidate-008` through `candidate-011`.

## Exit evidence

Record named red acceptance checks before implementation and pass the
independent gate and clean-room fence after build. The candidate report must
identify the selected release, candidate state identity, actual origin, exact
owner identity by safe digest, independently observed peer, media path, turn
and conversation identities, non-silent audio measure, foreign denial,
restart continuity, and rollback outcome. A source-of-truth comparison must
bind the public turn to the candidate's private session authority. The report
also verifies that the existing service remained healthy and its resource
identities did not change. Keep credentials and private speech out of shared
evidence.

The missing tailnet login, exact owner login, distinct authorized device, and
foreign peer are explicit prerequisites. If absent, keep their external gates
open and report a prepared candidate only. Do not infer public media or owner
authorization from a same-host test.

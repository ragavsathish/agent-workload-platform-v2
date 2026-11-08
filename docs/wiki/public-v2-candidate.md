# Isolated public candidate

Cites [public deployment rules](public-deployment.md), [deployed voice rules](deployed-voice-journey.md), [running host observation](../../raw/v1/public-candidate-host-observation.md), and [candidate decisions](../plan/22-public-v2-candidate/decisions.md).

## Candidate identity and isolation

A candidate has its own durable identity, state, address, and owner entry. Its
start, restart, failure, and removal must leave the existing owner service's
address and durable state intact. The candidate cannot be considered isolated
merely because it has a different label; every shared resource and endpoint
must be accounted for before it starts. A blocked prerequisite leaves the
candidate unexposed and the existing service available.

## Owner entry

One exact, verified owner identity authorizes the candidate. The public entry
checks that identity before signaling, selected history, song audio, task
status, or knowledge reads. Caller-provided identity, local-access hints, and
an inner service credential do not grant owner authority. A foreign identity
is denied while the exact owner succeeds on the same healthy candidate. The
candidate's origin and identity are recorded from the actual connection, not
assumed from a proposed address.

## Spoken journey and release decision

The owner starts a conversation from a separate device. A complete journey
requires a reachable media path, timed incoming speech, a settled owner turn,
non-silent reply audio, and the same turn in selected history after restart.
Signaling alone leaves spoken readiness unqualified. The candidate records its
observed media path and proves that a failed path cannot become a successful
spoken turn. A failing release is recoverable to the last qualified version
without losing accepted owner work.

The release decision keeps local health, public owner entry, live media,
foreign denial, restart, and rollback as separate evidence. Missing exact
owner identity, an authenticated candidate node, a distinct owner device, or
a foreign peer is reported as a missing gate. It does not become a passed gate
through a local substitute.

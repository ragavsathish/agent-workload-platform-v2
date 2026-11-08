# Slice 22 decisions

Cites [candidate rules](../../wiki/public-v2-candidate.md), [slice 06 decisions](../06-public-deployment/decisions.md), [host observation](../../../raw/v1/public-candidate-host-observation.md), and [Tailscale Serve documentation](https://tailscale.com/docs/features/tailscale-serve).

## D93: candidate isolation is a measured condition

Every candidate resource has an identity distinct from the running owner
service. Preflight refuses a collision before any candidate is started.
Existing resources are observed before and after the candidate journey. The
pruned branch accepts a different service label while sharing durable state or
an occupied endpoint.

## D94: owner identity is established by the entry

The candidate's actual private origin and exact owner login are bound to the
entry before any owner capability is exposed. A service credential or
client-supplied identity cannot substitute for a verified connection. A
tagged or otherwise identity-less peer is denied. The pruned branch uses a
placeholder owner or trusts a forwarded claim without proving its source.

## D95: public speech requires independent media evidence

The separate-device gate records the selected media path and completed
two-way audio, then compares the settled turn with the candidate's authority.
Signaling success, an open page, or a same-host result cannot qualify speech.
The pruned branch marks public voice ready after an offer succeeds.

## D96: exposure requires a current safe entry

Before the candidate's private route is enabled, preflight confirms the entry
is current enough for the selected secure mode and has a durable, distinct
identity. The [October 2026 bulletin](https://tailscale.com/security-bulletins)
sets the version threshold for the current entry. The pruned branch exposes a
candidate using the previously pinned older entry without checking its
running version.

## D97: release proof is paired and reversible

Exact-owner success and foreign-user denial are checked on the same healthy
candidate. Restart and rollback are verified against the same selected
conversation and accepted task, with existing service health independently
observed. The pruned branch counts an outage as denial or a stopped candidate
as successful rollback.

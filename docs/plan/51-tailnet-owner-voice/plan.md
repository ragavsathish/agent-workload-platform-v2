# Slice 51: private owner voice journey through to the host runtime

Cites [tailnet owner voice rules](../../wiki/tailnet-owner-voice.md), the [deployed voice journey](../../wiki/deployed-voice-journey.md), [live owner conversation](../../wiki/live-session-integration.md), the [host operations boundary](../39-k3d-semaphore-operations/plan.md), and [Slice 47's remaining app-plane qualification](../47-k3d-linux-app-plane/plan.md).

## Goal

Let the configured owner open AWP v2 over a private network address and complete a real spoken turn through the owner workspace, voice Edge, host Pi and inference services, and Grain-owned selected conversation. The owner can hear the response and read the safe transcript for the same turn.

## Scope

- Establish one private owner entry whose authenticated identity reaches the owner Edge as verified identity.
- Allow only the configured owner to use owner routes; network membership and caller-supplied identity values are not sufficient authority.
- Deliver the secure browser origin, microphone start gesture, HTTP signaling, bidirectional WebRTC media, and response path end to end.
- Route one final recognized utterance to the selected conversation and persist/recover the same safe turn through Grain.
- Report unavailable host or media capabilities clearly; do not claim the journey is ready from page health alone.

## Boundaries

This is one owner journey, not a public product release. The private entry must remain private to the user's tailnet. Public sharing, public relay, and unrelated host-service mutation are outside the slice. Preserve existing v1 state and Grain identity. Do not change the event-store authority or bypass the owner client's explicit microphone action.

## Acceptance

See [Gherkin scenarios](spec.feature). Completion requires a private URL, authorized owner identity, successful secure browser access, real bidirectional voice media across the network boundary, a host-backed Pi turn, audible response, safe transcript in the selected Grain conversation, and an unauthorized caller denial. Missing prerequisites or failed media must be shown as a visible unavailable state rather than success.

## Tickets

- [01: private owner entry and identity](tickets/01-private-entry.md)
- [02: browser media path](tickets/02-browser-media.md)
- [03: host-backed spoken turn and recovery](tickets/03-host-turn-and-recovery.md)
- [04: end-to-end qualification and release evidence](tickets/04-qualification.md)

## Implementation status

The deployment is live from public revision `main@sha1:29ad0b798df2fc6bfd94975044cce1744cb17c7c`.
The helper reused the stopped v1 Tailscale state, so no auth key or new device
approval was needed. The Edge, Traefik, and Tailscale containers are 3/3 Ready;
Tailscale Serve reports the private `pipecat-edge.tailf6d713.ts.net` route to
Traefik, and the configured Edge owner matches the authenticated node login.
An in-pod request through the Traefik owner route returned HTTP 200, and the
Edge can reach the host Pi broker. Opening the MagicDNS URL from this Mac's
browser failed DNS resolution because this Mac is not connected as a Tailscale
client. Browser microphone, bidirectional WebRTC media, and a complete host
turn still require verification from an authorized tailnet client.

## Exit evidence

Record the generated private hostname and secure response, verified owner identity match (redacted to match/no-match), unauthorized request denial, browser/media connection result, one real final utterance and response media, safe transcript/selected conversation match after a fresh service start, and host capability health. Record that no public sharing was enabled. Do not record OAuth credentials, owner content, transcript text, or raw private session data.

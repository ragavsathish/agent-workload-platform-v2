# Ticket 01: private owner entry and identity

Cites [Slice 51 scenarios](../spec.feature) and [tailnet owner voice rules](../../../wiki/tailnet-owner-voice.md).

Implement scenario `@voice-tailnet-001`, `@voice-tailnet-002`, and `@voice-tailnet-003`. Give the voice Edge pod a persistent Tailscale identity and private Serve HTTPS route on port 443 to the Traefik loopback listener. Traefik requires the identity header supplied by Tailscale Serve, runs Edge owner authentication, then routes to Edge. Preserve the header, strip caller-supplied local markers, and keep Semaphore separate. Capture the final hostname and login from the authenticated tailnet node, not from a placeholder.

Deployment wiring is in `impl/deployment/k3d/voice/tailnet.yaml`,
`edge-plane.yaml`, and `tailnet-up.sh`. The helper reuses the prior v1 node
state when safe or waits for browser-based device approval. Live enrollment
and identity-denial qualification remain outstanding.

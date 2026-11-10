# Ticket 01: private owner entry and identity

Cites [Slice 51 scenarios](../spec.feature) and [tailnet owner voice rules](../../../wiki/tailnet-owner-voice.md).

Implement scenario `@voice-tailnet-001`, `@voice-tailnet-002`, and `@voice-tailnet-003`. Give the voice Edge pod a persistent Tailscale identity and private Serve HTTPS route on port 443 to its loopback listener. Preserve the owner identity header, strip caller-supplied copies, verify the configured-owner match, and keep Semaphore separate. Capture the final hostname from the authenticated tailnet node, not from a placeholder.

Deployment wiring is in `impl/deployment/k3d/voice/tailnet.yaml`,
`edge-plane.yaml`, and `tailnet-up.sh`. Live enrollment and identity-denial
qualification remain outstanding until the owner supplies the key locally and
an authorized/unauthorized tailnet client is available.

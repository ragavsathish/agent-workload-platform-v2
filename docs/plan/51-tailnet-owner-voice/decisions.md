# Slice 51 decisions

Cites the [Slice 51 plan](plan.md), [Gherkin scenarios](spec.feature), [Tailscale Kubernetes sidecar guide](https://tailscale.com/docs/solutions/connect-kubernetes-pods-to-tailnet-using-sidecar), [Tailscale Serve identity-header documentation](https://tailscale.com/docs/features/tailscale-serve), and [Pipecat Small WebRTC documentation](https://github.com/pipecat-ai/docs/blob/main/api-reference/server/services/transport/small-webrtc.mdx).

## D01 — Use Tailscale device login with Traefik as the HTTP ingress

Run Tailscale, Traefik, and the voice Edge in the same Pod network namespace. Tailscale Serve terminates private HTTPS on 443 and proxies to Traefik's loopback HTTP entry point on 8080. Traefik checks the verified `Tailscale-User-Login` through the Edge's `/api/ingress-auth` endpoint, then proxies to the Edge's loopback HTTP listener on 7860. Remove the Kubernetes Service and bind Edge HTTP to loopback so callers cannot bypass ingress with a Pod or Service IP. The shared Tailscale interface also gives SmallWebRTC a direct tailnet media path. The Deployment uses one replica and `Recreate` so two Tailscale daemons never mount the single-identity PVC concurrently; voice has brief downtime during rollout. Do not enable Funnel or a public route.

## D02 — Authenticate at the trusted ingress

The Traefik route accepts requests only when Tailscale Serve supplied `Tailscale-User-Login`, removes any `X-Voice-Local-Ingress` marker, and calls the Edge owner-auth endpoint before proxying. The Edge still validates the exact configured owner. Tailscale Serve strips caller-supplied Tailscale identity headers and supplies the verified identity. Edge's HTTP listener binds only to pod loopback and has no Kubernetes Service, closing the direct-HTTP spoof path. The exact MagicDNS HTTPS origin is applied to the Edge browser-origin policy after node identity is confirmed.

## D03 — Qualify the direct WebRTC media path

A successful HTTPS page and signaling route is not sufficient for SmallWebRTC. The browser and Edge must establish bidirectional UDP media over their Tailscale peer path across the Docker/k3d boundary. The current deployment has no configured ICE server list or TURN service. Do not claim the path works from page health; qualify real ICE selection and bidirectional audio from a separate authorized tailnet device. If the direct tailnet candidate cannot be selected, keep voice unavailable and design a separately reviewed private relay; do not expose a public TURN relay or arbitrary UDP range as a shortcut.

## D04 — Reuse the prior node state or approve device login in a browser

Prefer the stopped v1 Docker volume `pipecat-mlx-voice_tailscale-state` when it contains the prior node state. The helper verifies no running container is using that volume, copies state into the k3d PVC without displaying it, then starts the Tailscale sidecar with that identity. If no state is present or usable, Tailscale provides its normal device approval URL; the helper opens it in the user's browser and waits for approval. No auth key or OAuth secret is required or stored. The helper reads the final node DNS name and authenticated login from Tailscale status and sets the Edge owner and origin values. The owner still needs tailnet policy that allows HTTPS and direct media traffic; this slice does not edit tailnet admin policy.

## D05 — Keep Grain as owner-state authority

Tailscale authenticates network identity only. Edge's configured owner check and Grain's existing owner-scoped command/query checks remain authoritative for voice and durable data. Successful transport health never substitutes for owner turn evidence.

## D06 — Preserve the existing app and host services until full qualification

Reconcile the new route only after the identity/origin and media path are qualified. Do not restart host services for route setup. Use the existing host-bound voice services and their scoped credentials; a host broker readiness response alone is not proof of a completed voice turn.

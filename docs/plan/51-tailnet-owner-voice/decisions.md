# Slice 51 decisions

Cites the [Slice 51 plan](plan.md), [Gherkin scenarios](spec.feature), [Tailscale Kubernetes sidecar guide](https://tailscale.com/docs/solutions/connect-kubernetes-pods-to-tailnet-using-sidecar), [Tailscale Serve identity-header documentation](https://tailscale.com/docs/features/tailscale-serve), and [Pipecat Small WebRTC documentation](https://github.com/pipecat-ai/docs/blob/main/api-reference/server/services/transport/small-webrtc.mdx).

## D01 — Give the voice Edge a private tailnet identity

Run one Tailscale kernel-networking sidecar in the voice Edge Pod. Its tailnet interface shares the Edge network namespace, so Tailscale Serve can provide private HTTPS for signaling and the Edge can advertise a direct tailnet ICE candidate for WebRTC media. Keep Semaphore's loopback operator Ingress separate. Do not make the Edge a public Ingress or enable Funnel. The host-backed k3d node exposes `/dev/net/tun` to pods, which the sidecar needs for kernel networking.

## D02 — Trust identity only from Tailscale Serve

The Edge expects `Tailscale-User-Login` and validates that identity against its configured owner allowlist. Tailscale Serve strips caller-supplied Tailscale identity headers and adds verified identity headers for tailnet requests. Serve proxies only to `127.0.0.1:7860`; the Kubernetes service remains ClusterIP and is not the owner entry. The exact MagicDNS HTTPS origin must be allowed by the Edge browser-origin policy before the route is enabled.

## D03 — Qualify the direct WebRTC media path

A successful HTTPS page and signaling route is not sufficient for SmallWebRTC. The browser and Edge must establish bidirectional UDP media over their Tailscale peer path across the Docker/k3d boundary. The current deployment has no configured ICE server list or TURN service. Do not claim the path works from page health; qualify real ICE selection and bidirectional audio from a separate authorized tailnet device. If the direct tailnet candidate cannot be selected, keep voice unavailable and design a separately reviewed private relay; do not expose a public TURN relay or arbitrary UDP range as a shortcut.

## D04 — Enroll the Edge without putting a credential in source

The host has no Tailscale client identity and the cluster has no Tailscale auth material. The local setup helper will prompt for an owner-provisioned, preauthorized Tailscale auth key and send it directly to Kubernetes Secret creation; the value is never accepted as a CLI argument, echoed, logged, or written into Git. Persist the resulting node state in a separate Kubernetes Secret so pod restarts retain the same private hostname. The owner must ensure tailnet policy permits their client device to reach the node for HTTPS and direct ICE traffic; this slice does not edit tailnet admin policy.

## D05 — Keep Grain as owner-state authority

Tailscale authenticates network identity only. Edge's configured owner check and Grain's existing owner-scoped command/query checks remain authoritative for voice and durable data. Successful transport health never substitutes for owner turn evidence.

## D06 — Preserve the existing app and host services until full qualification

Reconcile the new route only after the identity/origin and media path are qualified. Do not restart host services for route setup. Use the existing host-bound voice services and their scoped credentials; a host broker readiness response alone is not proof of a completed voice turn.

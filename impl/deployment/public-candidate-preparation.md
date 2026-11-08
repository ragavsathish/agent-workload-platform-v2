# Slice 22 public candidate: configuration review, not yet applied

Cites the [slice plan](../../docs/plan/22-public-v2-candidate/plan.md),
[host observation](../../raw/v1/public-candidate-host-observation.md),
[existing deployment runbook](README.md), and
[Tailscale Serve documentation](https://tailscale.com/docs/features/tailscale-serve).

This file proposes non-secret configuration for the build turn. No container,
service, tailnet node, policy, or route has been created from it. Exact runtime
values must be checked again immediately before start; free ports observed
today can become occupied.

| Resource | Proposed v2 candidate | Collision check |
| --- | --- | --- |
| Compose project | `awp-v2-public-candidate-22` | `docker compose ls`; never use v1 `pipecat-mlx-voice` project |
| Tailscale node name | `awp-v2-candidate-22` | `tailscale status` after candidate authentication; never reuse v1 `pipecat-edge` node or its state volume |
| Tailscale state | candidate project's own persistent named volume | Inspect volume names and mounts before and after start |
| Native state | outside either checkout, under a new private candidate directory | Require an empty/new directory, mode 0700, and no path overlap with v1 or previous v2 state |
| Native listener ports | Grain `9747`, Pi `9746`, broker `9743`, gateway `9744`, optional song owner `9748` | `lsof` and an actual bind check before start; current v1 holds `9443`, `9444`, `9446` |
| Candidate proxy listener | sidecar loopback `127.0.0.1:8080`, in the candidate network namespace only | No host published port; v1 host `127.0.0.1:8080` remains untouched |
| Candidate origin | `https://<actual-candidate-magicdns-name>` | Use the Serve-reported origin after the new node joins; reject a guessed suffix |
| Edge owner allowlist | exactly one verified owner login | Copy the confirmed login into private mode-0600 configuration, never a placeholder |
| Edge broker and gateway targets | `host.docker.internal:9743` and `host.docker.internal:9744` | Request actual v2 health and a candidate Grain-bound owner read; v1 endpoints on `9443`/`9444` must not satisfy checks |
| Peer gate | one different owner device plus one foreign or identity-less peer | Record safe peer identity and success/denial on the same healthy candidate |

## Preflight that must pass before exposure

1. Check the exact candidate image's running `tailscale version` is at least
   `1.104.4` before configuring HTTPS Serve. The current v2 pinned digest is
   the same as v1's observed `1.102.5` image and needs a reviewed replacement.
   See [Tailscale's October 2026 bulletin](https://tailscale.com/security-bulletins).
2. Validate the selected Compose project name, project-scoped volumes, image,
   networks, and lack of host port bindings with rendered configuration and
   runtime inspection. Snapshot only safe v1 container IDs, mounts, health,
   and port bindings before candidate start; compare afterward.
3. Validate private service and edge configuration separately. The service
   file must point every state directory and native listener to candidate
   resources. The edge file must contain the exact owner and actual origin,
   and target the candidate broker and gateway. Generate distinct candidate
   tokens; no v1 secret or data volume is reused. Keep the files mode 0600
   outside Git and do not print their values.
4. Confirm the protected signaling path is reachable only through Serve and
   proxy, while direct LAN and tailnet access to edge, Grain, broker, gateway,
   and Pi fails. Verify a forged owner header and local marker are refused.
5. Gather and inspect ICE candidates from the actual separate owner device and
   candidate edge. If no candidate pair carries both directions, configure a
   bounded reachable media path before any spoken readiness claim. Do not
   infer media reachability from HTTPS signaling or same-host audio.

## Inputs that are still missing

- A candidate Tailscale login method and tailnet policy that grants the exact
  owner device access to the new node on TCP 443.
- The exact owner login as Tailscale Serve will forward it and the actual
  candidate MagicDNS origin after login.
- A separate authorized owner device for the full media probe, plus a foreign
  or identity-less peer to prove denial on a healthy candidate.
- A current Tailscale image digest that passes the version gate.

The existing `qualify_public.py` can run on the distinct owner device, but the
build must make its report candidate-specific and pair it with foreign denial,
candidate Grain comparison, restart, rollback, and the unchanged-v1 snapshot.
The current same-host release report cannot satisfy those external gates.

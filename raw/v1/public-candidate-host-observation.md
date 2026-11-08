# Public candidate host observation, 2026-11-08

Read-only observation of the currently running v1 deployment and the already
committed v2 release candidate. This file contains no credentials, owner login,
tailnet name, or private conversation data. The source v1 tree is pinned in
[the manifest](manifest.md); its running composition has since changed and is
recorded here only at the capability and port level.

| Observation | Evidence |
| --- | --- |
| Existing deployment | `docker compose ls --format json` showed project `pipecat-mlx-voice` running 13 containers from the v1 voice deployment. |
| Existing owner edge | `docker ps` showed healthy `tailscale`, `traefik`, and `voice-edge` containers in that project. |
| Reserved host listeners | `lsof -nP -iTCP -sTCP:LISTEN` showed v1-owned `127.0.0.1:8080`, `:9443`, `:9444`, and `:9446`. No listener was observed on `:9447` at the observation time. |
| Existing tailnet state | The v1 Tailscale container mounts a named volume at `/var/lib/tailscale` and publishes only its local test listener to host loopback. Its image is pinned to digest `c507f3a2a6ab1cabd8d809b98edeb41edbd5c3fb6ad9632ffd098b4c7d0b4065`. |
| Installed Tailscale version | `docker exec pipecat-mlx-voice-tailscale-1 tailscale version` returned `1.102.5`. The v2 candidate composition currently pins the same digest. |
| Other active projects | `docker compose ls` also listed `image-builder`, `medplum`, and `utils`; the candidate must avoid their names, volumes, networks, and published ports. |
| V2 release evidence | The v2 local release report records a private same-host journey. It does not establish a separate-device tailnet route or live public media. |

Tailscale's [Serve documentation](https://tailscale.com/docs/features/tailscale-serve)
states that Serve strips client identity headers and adds verified tailnet login
for user devices, but not tagged devices. It recommends a local-only backend.
The [container parameters](https://tailscale.com/docs/features/containers/docker/docker-params)
state that a persistent state directory retains the same node identity across
container restarts. The [October 2026 security bulletin](https://tailscale.com/security-bulletins)
advises version `1.104.4` or newer for HTTPS Serve. The candidate must check the
running version before enabling a public route.

No v1 container, network, volume, route, or tailnet policy was changed during
this observation. Neither an exact v2 owner login, a v2 MagicDNS origin, an
authenticated v2 node, nor a distinct owner and foreign peer was established.

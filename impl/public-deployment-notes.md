# Slice 06 implementation topology and qualification notes

Cites the [slice plan](../docs/plan/06-public-deployment/plan.md), [pinned composition](../raw/v1/deployment-compose.yml), [routes](../raw/v1/traefik-routes.yml), [tailnet setup](../raw/v1/tailnet-login.py), [source lifecycle](../raw/v1/voice-makefile), [host requirements](../raw/v1/host-requirements.md), and [slice 05 implementation record](deployed-voice-journey-notes.md).

## V1 topology to derive from, not copy blindly

V1 runs Tailscale Serve at a MagicDNS HTTPS origin and an owner-authenticated reverse proxy in the shared edge network namespace. Serve strips client identity headers and injects the verified tailnet login. The proxy's owner check applies before the voice route; its local-test entry is bound to host loopback and marks requests separately. The edge verifies exact origin and owner before signaling. A signed, single-use path token protects the live WebSocket path per the [host requirements](../raw/v1/host-requirements.md); inspect the current v2 signaling protocol before copying that detail. V1's gateway, broker, and Pi services remain on loopback or private networks. [Source composition](../raw/v1/deployment-compose.yml), [routes](../raw/v1/traefik-routes.yml), and [edge](../raw/v1/edge-bot.py) are concrete references.

V1's `make start` prepares the deployment and native services; `make down` stops native services before the composed stack and preserves data. Service definitions have `RunAtLoad`, `KeepAlive`, and throttled restarts. V2 needs its own persistent Grain bridge, Pi voice, broker, edge, task owner, and scheduled worker with separate data directories and credentials. Use supervision that can show process identity, readiness, crash/restart count, and explicit stop order. The local slice 05 launcher is a qualification fixture, not a service manager. See [source lifecycle](../raw/v1/voice-makefile), [task owner](../raw/v1/service-task-owner.plist), [task worker](../raw/v1/service-task-worker.plist), and [Pi voice](../raw/v1/service-pi-voice.plist).

## Media path and authentication choices to resolve in build

Signaling over HTTPS does not carry RTP. Inspect the edge's ICE candidates from both a tailnet device and the server, verify selected candidate pair and packet flow, then choose reachable host candidates or a restricted relay. A loopback-only candidate, local ingress marker, or same-host peer cannot satisfy `deploy-003`. Bind the v2 public edge to the actual v2 Grain selection; the v1 listeners on `:9443` and `:9446` must never be credited as v2 evidence. Deny direct edge access from the LAN; only the identity proxy may set trusted owner fields. Test spoofed headers, foreign owner, direct service token, and disabled local-test ingress.

The [v2 media driver](qualification/voice_media.py) already sends 20 ms audio and checks non-silent output and Grain binding. Extend it only in the build invocation to record external origin and candidate pair, and run from a distinct device. It may remain a protocol peer instead of a browser if it traverses the same public entry and media route. Preserve the six local slice 05 scenarios as a regression suite, not as proof of public ingress.

## Worker and lifecycle verification

Admit a new task through the owner-facing path, with no preseeded executor. Observe the scheduler claim one attempt, launch the actual Gondolin-backed worker, and expose safe task progress. Restart the owner and worker during pending and executing states; assert task ID and claim continuity. A process health check cannot prove scheduling. Separate a worker's inability to start due to missing model or image assets from a scheduler failure. For rollback, record the prior qualified version and durable store snapshot or compatible migration plan, deploy a deliberately unready candidate, restore the prior version, and rerun public history and accepted-task checks.

## External gates and prerequisites

| Gate | Requires | Evidence | Local substitute limit |
| --- | --- | --- | --- |
| Owner entry and policy | Tailscale account, client on same tailnet, exact owner login, policy permitting only owner TCP 443, MagicDNS certificate | External HTTPS request, verified identity and foreign-user denial | Loopback cannot prove tailnet identity or policy |
| Public media | Distinct device, reachable ICE candidates or restricted relay, firewall permission | Candidate pair, RTP counters, recognized timed audio, non-silent reply | Same-host ICE cannot prove external reachability |
| Speech and Pi | Parakeet and Kokoro assets, provider credentials, Pi login, owner/service tokens | Fresh recognized utterance, one Grain turn, response audio | Mock or status endpoint cannot prove spoken journey |
| Task execution | Gondolin image, allowed workspace, artifact/Gitea access, worker credentials | Scheduler claim, real VM boot, task outcome | Seeded local worker cannot prove automatic scheduling |
| Restart and rollback | Persistent state paths, service supervisor, previous qualified version | Process IDs change, identities persist, public recheck | Restart of a local launcher is not host supervision |

The current host lacks both Tailscale CLI and Tailscale.app per the [slice 05 record](deployed-voice-journey-notes.md). The slice 06 build can implement local service supervision and adversarial ingress checks, but must report public entry and media as unqualified until that external setup exists. Keep credential values out of reports; record only presence, safe fingerprints, versions, IDs, and hashes.

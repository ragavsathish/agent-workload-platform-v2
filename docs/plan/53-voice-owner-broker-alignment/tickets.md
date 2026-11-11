# Slice 53 tickets

- [x] Capture the two live failures and their PromQL/runtime evidence.
- [x] Trace the Edge principal, gateway owner comparison, and persisted Grain owner semantics.
- [x] Map the single authenticated Tailnet principal to the existing Grain owner in the Edge-to-gateway contract; reject ambiguous owner configuration.
- [ ] Provide a safe, side-by-side v2 broker runtime and point the Edge at it only after its required routes pass authenticated probes.
- [x] Reconcile the owner mapping through Flux without mutating Grain secrets or the legacy host broker.
- [ ] Verify the v2 broker routes, non-owner denial, and a complete spoken owner task journey.

## Initial verification

- Authenticated in-pod GETs to `/health` and `/v1/pi/transcript` returned 200; `/v1/pi/tasks/routing` and `/v1/pi/turn/latest` returned 404 from the configured host broker.
- PromQL over 30 minutes showed approximately two additional `/api/tasks` 403s and two gateway `tasks` 4xxs. The two broker error counters did not increase during that window, but both direct broker routes still returned 404.
- Tailnet login hash matched the Edge allowlist hash and did not match the gateway owner hash. No identity, token, or owner payload was emitted.
- Source audit shows the existing Grain owner participates in owner-scoped reads/writes; changing it in place is not a safe repair without data migration. No runtime secrets or services were changed.
- Edge now authenticates the Tailnet principal first and sends the existing `grain-session.owner` value to task-gateway requests. Owner-scoped WebRTC tools, artifact reads, and task APIs use the same mapping; the Edge pod reads the owner key from the existing Secret.
- Flux applied revision `main@sha1:fe15cafc0fc54ea7b3423326adfcd5ebf1d6fc28`; image `awp-v2-voice-edge:slice53` rolled out with 3/3 containers ready. Two owner-scoped in-pod GETs to `/api/tasks` returned HTTP 200; only status and item count were emitted.
- PromQL after rollout shows the new Edge series returning `/api/tasks` 200 and the Gateway `tasks` series returning 2xx; `increase` over five minutes is zero for `/api/tasks` 403 and Gateway `tasks` 4xx. The 403 series count 18 belongs to the prior Edge/Gateway instance and did not increase.
- The v2 broker routes remain unavailable: authenticated requests to the legacy host broker still return 404. No active host service was restarted. The v2 broker/Pi runtime and spoken owner journey remain open deployment gates.

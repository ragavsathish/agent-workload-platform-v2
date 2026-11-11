# Slice 53 tickets

- [x] Capture the two live failures and their PromQL/runtime evidence.
- [x] Trace the Edge principal, gateway owner comparison, and persisted Grain owner semantics.
- [x] Map the single authenticated Tailnet principal to the existing Grain owner in the Edge-to-gateway contract; reject ambiguous owner configuration.
- [ ] Provide a safe, side-by-side v2 broker runtime and point the Edge at it only after its required routes pass authenticated probes.
- [ ] Reconcile through Flux and Semaphore without mutating the legacy host broker; prove rollback and owner-data continuity.
- [ ] Verify PromQL, non-owner denial, and a complete spoken owner task journey.

## Initial verification

- Authenticated in-pod GETs to `/health` and `/v1/pi/transcript` returned 200; `/v1/pi/tasks/routing` and `/v1/pi/turn/latest` returned 404 from the configured host broker.
- PromQL over 30 minutes showed approximately two additional `/api/tasks` 403s and two gateway `tasks` 4xxs. The two broker error counters did not increase during that window, but both direct broker routes still returned 404.
- Tailnet login hash matched the Edge allowlist hash and did not match the gateway owner hash. No identity, token, or owner payload was emitted.
- Source audit shows the existing Grain owner participates in owner-scoped reads/writes; changing it in place is not a safe repair without data migration. No runtime secrets or services were changed.
- Edge source now authenticates the Tailnet principal first and sends the existing `grain-session.owner` value to task-gateway requests. The Edge pod manifest references the same Secret key; no Secret value or Grain record is changed by this source update.
- This owner fix is not deployed yet. The broker contract remains blocked on a reviewed side-by-side v2 broker/Pi service deployment path; no active host service was restarted.

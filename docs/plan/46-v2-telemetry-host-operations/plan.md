# Slice 46: v2 telemetry and Semaphore host operations

## Goal
Make the v2 local deployment observable with operational AWP signals queryable from the dedicated v2 Prometheus, and establish Semaphore as the controlled operations path for macOS-native Pi, MLX, and Gondolin services. Preserve v1 and keep all routes private.

## Scope
- Use only the dedicated `awp-v2` OTEL-LGTM/Prometheus instance; do not query or reuse v1 telemetry.
- Trace the actual v2 metrics path from service instrumentation through OTLP to Prometheus, and prove it with real service-produced metrics and focused PromQL. A synthetic gauge only proves transport and is insufficient for acceptance.
- Add bounded, low-cardinality service/environment labels. Use `deployment_environment_name="awp-v2-local"` consistently (or record an evidence-based existing convention before implementation).
- Include readiness/availability signals for deployed k3d services and the v2 host-native services that are in scope. Do not infer a host service is observable from collector self-metrics.
- Qualify host-to-k3d and k3d-pod-to-host networking as separate paths. Semaphore runner host networking is not proof of pod-to-host connectivity.
- Use Semaphore's registered native macOS runner for host status and lifecycle operations. Begin with read-only inventory and health probes; expose lifecycle operations only with exact process/service targets, dry-run or explicit bounded action, and a rollback path.
- Keep Semaphore as operator UI and Flutter as owner UI. This slice does not claim Flutter deployment readiness unless its host broker route is independently proven.
- Keep all app/support workloads ClusterIP and operator access loopback-only. No public or tailnet route.

## Out of scope
- Stopping/changing v1, public/tailnet route creation, owner cutover, destructive cleanup, or rewriting candidate event history.
- Using v1's Prometheus/collector or public-candidate containers as v2 evidence.
- Any host mutation before read-only runner identity, inventory, path, and rollback checks pass.
- Treating a successful collector health endpoint or synthetic OTLP test as evidence that AWP services are instrumented.

## Sequence
1. Inspect current v2 services, instrumentation, metrics labels, Semaphore runner, host LaunchAgents, k3d routes and existing Slice45 evidence.
2. Record the telemetry signal contract, host operation inventory, and decision gates before implementation.
3. Implement/enable the minimum real metrics needed to represent v2 readiness and request/operation outcomes, avoiding owner data, prompts, payloads, credentials, IDs, or unbounded labels.
4. Verify service-produced OTLP samples arrive in the dedicated v2 Prometheus and query meaningful AWP-specific PromQL with exact labels and expected values.
5. Verify Semaphore runner can perform read-only status checks against the approved host inventory and local k3d API. Separately test pod-to-host only against an explicitly approved harmless endpoint.
6. Add only narrow host lifecycle tasks whose target matching, action, output redaction, restart policy, and rollback are proven. Do not stop v1.
7. Record what remains unqualified, especially Flutter/Grafana MCP, cross-host metrics, and cutover gates.

## Acceptance
- The dedicated v2 collector is ready and distinct from v1 telemetry.
- At least one real metric emitted by an AWP v2 service is queryable in v2 Prometheus; PromQL checks include environment/service labels and verify expected semantics, not merely HTTP success.
- A bounded readiness query covers the required k3d services, and any host-service claim is backed by a real signal or an explicitly qualified scrape/export path.
- Semaphore runner performs read-only probes for the approved native host inventory and k3d API using its host network context; no secret or sensitive output is exposed.
- Pod-to-host result is separately recorded; it is not assumed from the runner check.
- Host lifecycle tasks, if enabled, act only on exact v2-native targets and include explicit verification/rollback; v1 services remain unchanged and running.
- No public/tailnet AWP route is present.

## Execution record (2026-11-09)

- Added bounded gateway response metrics at the HTTP response boundary. Labels are only `http_route` (`health`, `tasks`, `knowledge`, `music`, `incidents`, `other`), method (`GET`, `POST`, `OTHER`), and response class (`1xx`–`5xx`). No URL IDs, owner identity, payload, prompt, task/session IDs, credentials or artifact paths are labels. Metrics export is optional and non-fatal to requests.
- Export target is only `http://otel-lgtm.awp-v2.svc.cluster.local:4318/v1/metrics`. Resource attributes include service `awp-v2-gateway`, namespace `awp-v2`, version `slice46`, and environment `awp-v2-local`.
- Rebuilt local linux/arm64 image `awp-v2-gateway:slice46`: image manifest digest `sha256:5cf4cc735018db92d2efea9f2e1009828f7546eb69e623fdf390643d7c9a4b49`, runtime image ID `sha256:698c3fb2644a0b589968ecebe0d6d85bcd1b11c914ad8511562e52e0f75f0439`. Deployment rolled out successfully and `/health` returned `{"status":"ready"}`.
- The v2 Prometheus `/api/v1/query` returned `awp_v2_gateway_http_requests_total` with `service_name="awp-v2-gateway"`, `deployment_environment_name="awp-v2-local"`, `http_route="health"`, `http_request_method="GET"`, `http_response_status_class="2xx"`; observed cumulative value was 12 and increased on subsequent probes. `awp_v2_gateway_ready_ratio{service_name="awp-v2-gateway",deployment_environment_name="awp-v2-local"}` returned 1. Metric name `_ready_ratio` is the actual Prometheus translation of the unit-1 OTLP gauge. This is service-originated evidence from v2, not the earlier Slice45 synthetic signal.
- Added Semaphore template `AWP v2 host runtime read-only probe` (template ID 12), pinned to runner tag `macos-host`, in the existing local Grain migration project. Task `2147483565` completed successfully. Its allowlisted output reported all seven reviewed native host LaunchAgent labels `loaded`, local k3d `/readyz` ready, and Grain, gateway, Gitea, S3Mock, Oxigraph, and OTEL-LGTM pods ready. The helper suppresses PIDs, process arguments, service environment values and tokens.
- Correction from Slice 47: the original TCP-only probe targeted `host.docker.internal:9743`. That connection succeeded, but 9743 was not verified as the broker port and must not be described as a broker-path check. Source inspection of the loaded broker LaunchAgent and broker server confirmed port 9443. Slice47 then verified a TCP/HTTP route from the Edge pod to `host.docker.internal:9443/health` with the single `INFERENCE_BROKER_TOKEN` read from the active broker dotenvx file; authenticated health returned HTTP 200. No host credential file was changed, and no token value was included in task output.
- No host lifecycle mutation was executed. The native inventory only proves labels are loaded; per-service health and exact safe stop/restart behavior plus rollback remain gates before lifecycle templates are enabled. No v1 service was stopped. No public/tailnet route was added.

## Remaining gates

- Host Pi/MLX/Gondolin metrics are not yet present in the v2 Prometheus; only the gateway is currently proven as an AWP-originated series.
- Full owner voice flow through Flutter remains unqualified; the broker health path alone does not qualify browser identity, media transport, or an end-to-end Pi turn.
- Edge/Flutter deployment, standalone Grafana MCP, per-service host health and bounded native lifecycle operations remain unqualified. No v1 shutdown/cutover claim is made.

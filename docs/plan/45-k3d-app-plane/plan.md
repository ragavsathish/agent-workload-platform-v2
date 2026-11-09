# Slice 45: k3d app and support plane

## Goal
Deploy internal AWP v2 application/support services in the existing loopback-only k3d cluster, preserving Grain identity and all v1 runtime state. Host-native Pi, MLX inference, and Gondolin remain under Semaphore operations.

## Scope
- Deploy Python gateway from v2 source with Grain as authority.
- Deploy Gitea, S3-compatible artifact storage, and Oxigraph with durable claims.
- Qualify Flutter Edge only when broker, Grafana MCP, and required private routes are available; evaluate Grafana MCP only against isolated v2 Grafana.
- Keep workloads ClusterIP and operator access loopback-only. Prove pod-to-host separately from Semaphore runner host networking.
- Add only safe reviewed Semaphore templates.

## Out of scope
- Stopping or changing v1, public/tailnet routes, cutover, or PromQL claims.
- Reusing v1 telemetry, public candidate containers, or unrelated services.
- Host lifecycle mutation before read-only runner probes and inventory review.

## Sequence
1. Record source/config/image provenance and decisions.
2. Create isolated namespace workloads, persistent claims, and pinned images; no Ingress/NodePort.
3. Build local AWP images from v2 source and record resulting digests.
4. Verify actual HTTP/API paths and functional in-cluster connectivity.
5. Verify persistence, loopback-only operator access, and unchanged v1 runtime.
6. Enable only Semaphore templates whose path and side effects have been reviewed.

## Acceptance
- Grain remains 1/1 Ready on the same PVC and identity.
- Gateway calls Grain over cluster service DNS without writing deployment observations.
- Gitea, S3-compatible storage, and Oxigraph APIs work in-cluster and retain data after controlled restart.
- No public/tailnet route exists. v1 remains running and unchanged.

## Execution record (2026-11-09)

Deployed in namespace `awp-v2`:

- `agent-gateway` (local image digest `sha256:a262dbc10c99c6ccf2ff2b8bc28468f8265ba3f92b2bc3de5238fb2ba3d3ddfa`), using the pinned Python 3.13.7 base; gateway token is a separate Kubernetes Secret and Grain identity/token are mounted from `grain-session`.
- `gitea-0`, image `docker.gitea.com/gitea:28.1.0-rootless@sha256:b1a90409ae9afcd5a5725404c3639da7245248848b99e35332968891290b953e`, PVC `data-gitea-0` UID `f3fb0288-1e0d-48a2-9836-3bd5385f1d02`.
- `s3mock-0`, image `adobe/s3mock:5.0.0@sha256:c461c8f4234bd63540d2f3a3cabce69d814c78dc890a5af08727525a028e23da`, PVC `data-s3mock-0` UID `ce87ba33-3481-41d6-9ed9-a1f4678f4479`.
- `oxigraph-0`, image `oxigraph/oxigraph:0.5.11@sha256:7532c1f9aa5f28c0dc6a4243198f7f6144017fab7429bf6c164a89fec9411767`, PVC `data-oxigraph-0` UID `52d81f6e-f451-49da-99e7-612602219471`.
- `otel-lgtm-0`, image `grafana/otel-lgtm:0.30.1@sha256:bb182ac3174a20923fa58f00d1790fa71eb06cc0150078c0b0e8feb9e6611492`, PVC `data-otel-lgtm-0`; Grafana admin password is stored outside the checkout and mirrored to a Kubernetes Secret.

All application services are ClusterIP. No application Ingress or NodePort is present. Only the pre-existing Semaphore operator Ingress is in `awp-ops`; its host port remains loopback bound. No v1 container or LaunchAgent was stopped or changed.

Read-only functional probes from the gateway pod returned HTTP 200 for Gitea `/api/healthz`, S3Mock `/`, Oxigraph SPARQL ASK, and owner-authorized gateway `GET /v1/tasks` backed by Grain (zero tasks). Gateway readiness is a pure `/health` response and does not write deployment observations. Oxigraph retained a temporary SPARQL triple through pod recreation; S3Mock retained a temporary test object through pod recreation, then the object was deleted. Grain pod and stable PVC UID were unchanged.

Telemetry verification used only this new v2 LGTM instance: OTLP HTTP `/v1/metrics` accepted a synthetic `awp_v2_slice45_qualification=1` gauge (HTTP 200), and Prometheus returned it from a successful PromQL instant query with value `1`. This proves the local v2 OTLP-to-Prometheus query path only; no real AWP service metric has yet been observed, and it does not prove cutover readiness.

Deferred: Edge/Flutter deployment awaits a separately proven pod-to-host route to the loopback-only native inference broker plus reviewed Edge credentials/config. Grafana MCP remains undeployed pending reviewed token provisioning and caller auth wiring. Semaphore deployment/status/rollback and host lifecycle templates remain disabled because the native runner's k3d API access and pod-to-host path were not qualified in this slice. No public/tailnet route was configured. PromQL of AWP runtime signals and cutover remain open gates.

Telemetry caveat: Prometheus self-readiness succeeded, but `up{service_name=~".*awp.*"}` had no results when the parent checked. The synthetic qualification gauge is the only AWP-labeled series introduced in this slice; service instrumentation and live AWP PromQL remain unproven.

# Slice 47: remaining Linux app plane in k3d

## Goal

Reconcile the current AWP voice/task flow with the k3d v2 deployment. Move the required Linux-compatible services into k3d, keep native Pi, MLX, Gondolin, and trace-explainer processes host-managed through Semaphore, preserve Flutter as the owner UI, and do not expose an unqualified route.

## Scope and workload inventory

The active v1 project contained 13 Compose containers. Container labels and source files showed all used `products/voice-agent/deploy/compose.yml`; MLflow also used the `compose.mlflow.yml` override.

| Workload | Slice 47 disposition | Evidence or reason |
|---|---|---|
| Grain, agent gateway, Gitea, S3Mock, Oxigraph, OTEL-LGTM | Already deployed in v2 k3d | ClusterIP workloads; readiness and gateway PromQL rechecked. |
| `voice-edge` with Flutter web assets | k3d ClusterIP | 1/1 ready; Flutter `/`, `/api/features`, `/main.dart.js`, `/manifest.json` succeeded through temporary loopback port-forward. |
| Grafana MCP bootstrap and server | k3d ClusterIP | Dedicated viewer bootstrap/PVC and caller Secret; authenticated Edge `tools/list` (25 tools) and trace resource read succeeded. |
| `trace-render-worker` | k3d ClusterIP | 1/1 ready; health endpoint and S3Mock bucket listing succeeded. Full trace submit/render/view is still gated. |
| MLflow | k3d ClusterIP + persistent PVC | Pinned image, 10 GiB local-path PVC, SQLite backend. Health/search succeeded; artifact write/list through MLflow's proxy succeeded against v2 S3Mock. |
| Fluent Bit | Deferred as an explicit telemetry translation | The v1 Compose config uses `node_exporter_metrics` with `/host/proc` and `/host/sys` and labels samples `system=docker-desktop-vm,layer=vm`; it collects aggregate VM CPU, memory, load, disk, and network metrics, not pod logs. A k3d node collector would represent a different system. Do not mount macOS or Docker Desktop VM files into an app pod or claim signal parity. Current app health uses Kubernetes pod readiness and service-originated OTEL metrics; container logs remain accessible through Kubernetes. |
| Gitea Caddy ingress helper | Excluded | Gitea remains ClusterIP; the helper is not required by the owner flow. |
| Tailscale, Traefik | Excluded | No app route is qualified; no public/tailnet route is introduced. |
| Native Pi voice, MLX broker, Gondolin scheduler, trace-explainer MCP | Host-managed through Semaphore | Host architecture requirement. Trace-explainer MCP is an Edge viewer dependency whose in-cluster reachability remains unqualified. |

## Decisions

- All AWP app/support endpoints in this slice use ClusterIP. Temporary local port-forwards were used only for verification.
- Edge uses the existing Flutter UI. Do not weaken identity checks or enable local-ingress bypass to make a probe pass.
- Grafana MCP uses a dedicated v2 viewer service account. Tokens remain in Kubernetes Secrets and are excluded from evidence.
- The active broker credential is sourced from the dotenvx-managed host broker env by key name; only `INFERENCE_BROKER_TOKEN` was provisioned to the namespace Secret. The host file was not changed and no credential values are recorded.
- The authenticated broker health route is `http://host.docker.internal:9443/health`; port 9743 had only produced a TCP connection to an unverified endpoint and is not broker evidence.
- Fluent Bit's old Docker Desktop VM metric stream is not silently relabeled as k3d node telemetry. A separate slice should decide whether/how to translate node and host signals.
- Parent orchestration stopped the 13 v1 Compose containers after v2 qualification and preserved named volumes. v1 must not be used as v2 evidence.

## Acceptance and current result

- [x] Every active v1 Compose container has a k3d, host/Semaphore, or explicit exclusion disposition.
- [x] Edge and Grafana MCP are ClusterIP-only and ready; Flutter assets/features and authenticated Edge-to-MCP methods were checked.
- [x] Edge made an authenticated broker health request on port 9443 and received HTTP 200.
- [x] Trace worker is ready, its health route responds, and it can list the v2 S3Mock bucket. A real render has not been submitted.
- [x] MLflow is ready with persistent backend state; `/health`, experiment search, artifact upload, and artifact listing succeeded. The upload was a dedicated non-sensitive qualification artifact.
- [x] Fluent Bit's active v1 signal and the reason for deferring a k3d metric translation are documented.
- [x] k3d `/readyz` passes, all 10 v2 pods are 1/1 ready, Prometheus `/-/ready` passes, and gateway readiness PromQL returns 1 with exact bounded service/environment labels.
- [x] v1 Compose containers are stopped with named volumes preserved. Semaphore is the only Ingress, at localhost; no AWP public or tailnet route was added.
- [ ] Full owner trace viewer/render flow: Edge source proxies viewer requests to host trace-explainer MCP port 9450, but the Edge manifest lacks `PLAYWRIGHT_TRACE_VIEWER_INTERNAL_BASE_URL`; host service network reachability from the pod and real trace rendering are unqualified.
- [ ] Owner-authenticated live Pi turn and model-backed narration require a real owner session and qualified native host services; component health alone does not prove this flow.

## Execution record

The spec was written after reviewing the v1 base and MLflow Compose sources, active container labels, v2 source/manifests, and runtime. No v1 source or secret was modified. The active secret file was read only through a key-scoped extraction into a temporary mode-0600 file, which was cleaned up after Kubernetes Secret creation. No credential value was written to logs or docs.

Edge was built for linux/arm64 from the v2 checkout and imported to k3d. Edge and Grafana MCP use ClusterIP Services. Flutter assets/features succeeded through a temporary loopback port-forward. The Edge pod used the active broker token to GET `/health` on port 9443 and received HTTP 200. Authenticated MCP `tools/list` returned 25 tools and a trace resource read returned content.

The trace worker was built for linux/arm64 and deployed with bounded resources, scratch/temp emptyDirs, and S3Mock configuration. Its `/health` response was HTTP 200; an in-pod read-only ListObjects request to `agent-artifacts` succeeded. No trace submission or render occurred.

MLflow uses pinned `ghcr.io/mlflow/mlflow:v3.17.1-full@sha256:190306b1739b9646d6d39333594334b69ba14ab843db927712c790b50892f262`, a ClusterIP Service, and a 10 GiB local-path PVC. The container starts with SQLite at `/var/lib/mlflow/mlflow.db` and artifact destination `s3://agent-artifacts/mlflow`, proxied to the v2 S3Mock endpoint. Health and experiment search returned HTTP 200. A dedicated qualification experiment/run wrote and listed an artifact through the artifact proxy; the artifact URI scheme was `mlflow-artifacts`. The artifact was non-sensitive.

The bundled OTEL-LGTM Grafana previously started without a port-3000 listener. The StatefulSet was revised to set Grafana's bind address/port explicitly and to use bounded resources/probes. Current Grafana `/api/health` and Prometheus `/-/ready` pass. Final PromQL results: `awp_v2_gateway_ready_ratio{service_name="awp-v2-gateway",deployment_environment_name="awp-v2-local"}=1`; bounded health counter `awp_v2_gateway_http_requests_total{service_name="awp-v2-gateway",deployment_environment_name="awp-v2-local",http_route="health",http_response_status_class="2xx"}=328`.

All 10 v2 pods were 1/1 ready and k3d `/readyz=ok` at the consolidated check. Parent orchestration stopped all 13 v1 Compose containers without deleting named volumes. The v1 Compose project filter is empty. No AWP Ingress, NodePort, LoadBalancer, public route, or tailnet route exists; Semaphore remains the only localhost Ingress.

## Proposed next Loyly slice

**Slice 48: host trace viewer integration and observability parity.** Qualify the Semaphore-managed trace-explainer service and Edge-to-host route without adding a public route, configure the Edge viewer endpoint, and exercise a synthetic/non-sensitive trace through the worker and S3Mock. Separately decide whether to translate the legacy Docker Desktop VM signal into k3d node metrics. Do not claim k3d node metrics are equivalent to Docker Desktop VM or macOS host metrics.

# Slice 47 tickets

- [x] Inventory the active v1 Compose services and source/runtime flow.
- [x] Record placement decisions and route boundaries before implementation.
- [x] Deploy Pipecat Edge with its Flutter web assets as a private ClusterIP service.
- [x] Deploy Grafana MCP with dedicated v2 bootstrap/caller credentials and ClusterIP service.
- [x] Deploy trace-render worker with bounded resources, scratch space, and S3Mock access.
- [x] Deploy MLflow with a persistent 10 GiB backend PVC and v2 S3Mock artifact destination.
- [x] Reconcile Fluent Bit against source behavior and record why a k3d DaemonSet is not part of the current app flow.
- [x] Record placement/exclusion for all 13 v1 containers, including Caddy Gitea ingress helper, Tailscale, and Traefik.
- [x] Verify Edge Flutter assets/features over temporary loopback port-forward.
- [x] Verify Edge authenticated broker health on the source-confirmed 9443 endpoint.
- [x] Verify authenticated Edge-to-Grafana-MCP `tools/list` and trace resource read.
- [x] Verify MLflow health, experiment search, durable backend PVC, and an artifact write/list through S3Mock.
- [x] Verify trace worker `/health` and S3Mock bucket listing from inside the worker pod.
- [x] Re-query Prometheus readiness and real gateway PromQL with bounded service/environment/route labels.
- [x] Confirm all 13 v1 Compose containers are stopped and named volumes are preserved; no v1 evidence is used as v2 evidence.
- [x] Confirm there is no AWP public/tailnet route; Semaphore remains the only ingress at localhost.
- [ ] Qualify the full owner trace viewer/render loop through Edge. Edge source proxies viewer routes to the host trace-explainer MCP on 9450, but the v2 Edge manifest has no viewer endpoint, the host LaunchAgent is not qualified from the pod network, and no real trace render was submitted. Keep this as a release gate.
- [ ] Qualify real owner identity/session flow, live Pi turn interaction, and model-backed trace narration. These require native host service readiness and an owner-authenticated UI session; component health does not prove this flow.

## Follow-up slice proposal

**Slice 48: host trace viewer integration and observability parity.** First qualify the Semaphore-managed host trace-explainer service and the Edge-to-host route without adding a public route; then wire Edge to it and exercise a synthetic/non-sensitive trace through the worker and S3Mock. Separately decide whether to translate the legacy Docker Desktop VM CPU/memory/load/disk/network signal into k3d-node metrics. Do not label k3d-node metrics as equivalent to the Docker Desktop VM or macOS host metrics.

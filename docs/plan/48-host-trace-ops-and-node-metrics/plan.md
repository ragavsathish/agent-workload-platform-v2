# Slice 48: host trace viewer, Semaphore lifecycle, and k3d node telemetry

## Goal

Close the remaining local v2 operations gaps after Slice 47: verify the private Edge-to-host trace viewer path, exercise a synthetic trace through the renderer, establish a bounded Semaphore-mediated native-service lifecycle action, and determine whether the active Fluent Bit signal can be represented as k3d-node metrics without mislabeling the old Docker Desktop VM data.

## Current baseline

- v1 `pipecat-mlx-voice` Compose containers are stopped; named Docker volumes are preserved.
- v2 has ten 1/1-ready pods in `awp-v2`, ClusterIP app services, a ready k3d API, and a ready v2 Prometheus.
- The real v2 gateway readiness gauge and request counter pass PromQL with `service_name` and `deployment_environment_name` labels.
- Semaphore is the only Ingress and is bound to `localhost`.
- Edge-to-broker authenticated `/health`, Edge-to-Grafana-MCP tools/list, MLflow artifact proxy, and trace-worker S3 access passed in Slice 47.

## Scope

1. Confirm the active host trace-explainer LaunchAgent is loaded and healthy using Semaphore/read-only probe, and separately prove pod-to-host health at `host.docker.internal:9450` without changing the host bind interface.
2. Wire the Edge's internal viewer URL to the verified private host endpoint. Use only temporary loopback port-forwarding for owner-side checks.
3. Submit a synthetic, non-sensitive Playwright trace through the renderer and verify job status, artifact persistence, and viewer resource fetch. Do not use owner traces, session payloads, credentials, or live user data.
4. Add an exact-target Semaphore lifecycle operation for one native v2 dependency (broker) only if preflight, identity matching, verification, output redaction, and restart rollback are all proven. Keep other host services read-only if those gates are not met.
5. Reconcile the current Fluent Bit `node_exporter_metrics` signal with the k3d node. If feasible, deploy a least-privilege DaemonSet reading only the k3d node's `/proc` and `/sys`, emit distinct `system=k3d-node` labels, and query a sample in v2 Prometheus. Never read or claim equivalence to macOS or Docker Desktop VM metrics.
6. Add bounded host-runtime metrics to v2 Prometheus only if they can be exported through a qualified, loopback-only host-to-k3d path managed by Semaphore.

## Non-goals / safety boundaries

- No public, LAN, Tailnet, NodePort, LoadBalancer, or AWP Ingress route.
- Do not restart or stop v1 Compose; it remains down. Do not remove its named volumes.
- Do not stop the host broker before an alternate-health/rollback path is proven.
- Do not broaden a host service bind to `0.0.0.0`, disable authentication, or bypass owner identity.
- Do not expose tokens, owner data, trace payloads, session IDs, or unbounded labels in task output, logs, or documentation.
- Do not call Docker Desktop VM metrics “k3d node” or “macOS host” metrics.

## Acceptance

- Edge pod reaches the host trace-explainer health/API on the reviewed private bridge; viewer endpoint is configured and the trace viewer path is exercised through a loopback-only owner-side port-forward.
- A synthetic trace is submitted to the trace-render worker; completion and artifact fetch/list are verified without owner data.
- Any enabled Semaphore lifecycle task is restricted to an exact allowlisted service, verifies readiness before and after, and can restore the pre-operation state. If this cannot be proven safely, leave the service read-only and record the specific gate.
- Fluent Bit is either deployed against k3d-node paths with distinct bounded labels and a PromQL proof, or explicitly deferred with the missing technical capability and impact documented. No VM/macOS path is mounted into a pod.
- Any host metric in Prometheus has a real host-origin signal and a qualified loopback-only transport path.
- After changes, all v2 pods and k3d `/readyz` pass, v2 Prometheus is ready, gateway PromQL remains correct, and no AWP public/Tailnet route exists.
- v1 Compose remains stopped and its persistent volumes remain present.

## Sequence

1. Capture the current runtime baseline and add this spec before changes.
2. Use Semaphore for host service read-only inventory/health; test the private trace-explainer route from the actual v2 pod.
3. Configure and verify Edge trace viewer routing; run only a synthetic trace through worker/S3Mock.
4. Implement the exact-target Semaphore lifecycle action only after its rollback checks pass.
5. Implement or defer Fluent Bit/k3d-node metrics based on actual scrape/export compatibility.
6. Recheck readiness, PromQL, ingress, and v1-down state; record all remaining owner-flow gaps.

## Execution record

Pending Slice48 implementation and runtime qualification.

## Execution record (2026-11-09)

- Read-only host and pod probes confirmed `http://127.0.0.1:9450/api/health` and `http://host.docker.internal:9450/api/health` return HTTP 200. The host process remains loopback-bound.
- Edge is configured with `PLAYWRIGHT_TRACE_VIEWER_INTERNAL_BASE_URL=http://host.docker.internal:9450`; its rollout completed and the pod is 1/1 Ready. A loopback-only request to `/api/features` returned 200. A request to the protected trace resource without owner identity returned 403, confirming the auth gate. No identity was fabricated or bypassed; the owner viewer fetch remains gated on an authenticated owner session.
- A generated synthetic Playwright archive (one 1x1 blank PNG frame, synthetic page/trace IDs, no real trace/session data) was submitted to `trace-render-worker` over a temporary `127.0.0.1` port-forward. Render `aaaaaaaaaaaaaaaaaaaaaaaaaaaa0048` completed in about 16 seconds; its manifest reports 1 screenshot, 0 actions, 0 action errors. Worker status returned the manifest, and GET `/v1/renders/aaaaaaaaaaaaaaaaaaaaaaaaaaaa0048/video` returned HTTP 200 with a 414,567-byte MP4. The renderer completed local broker TTS. This proves worker persistence and artifact fetch; it does not prove the owner-authenticated Edge video route, which requires the unavailable owner identity.
- Added a Fluent Bit 4.2.0 DaemonSet pinned to image digest `sha256:aff743e1a5e61d036d10a4472d67cdd7a6b408c92218eb86e3f301e2bac7817a`. It reads only the k3d node's `/proc` and `/sys` via read-only hostPath mounts; it has no host macOS/VM path mounts, service-account token, writeable root, or elevated capabilities. The service is ClusterIP and metric labels identify `system=k3d-node,layer=node`.
- Prometheus startup inspection showed the real process reads `/otel-lgtm/prometheus.yaml`. The initial mount targeted an unused path, leaving Prometheus ready but with no configured targets; this was corrected. The deployed ConfigMap retains the bundled OTLP resource-label and storage settings, Prometheus self-scrape, and adds `fluent-bit-k3d-node`. Prometheus is ready; `up{job="fluent-bit-k3d-node"}` returned 1 and `node_cpu_seconds_total{system="k3d-node"}` returned node series. Gateway readiness remains 1 for `deployment_environment_name="awp-v2-local",service_name="awp-v2-gateway"`.
- The setup script CLI guard was added and exercised: `--help` prints that the script provisions/pins local Semaphore state and exits without provisioning; an unknown argument exits 2 before side effects. The script was not rerun without arguments.
- Semaphore project was inspected read-only after the earlier accidental `--help` provisioning. Current pinned revision is `37c6415b01bc177c4a91a92ebb6f1198608edc36`; templates 1–12 were present, including migration/preflight, host runtime probe, and host lifecycle templates. The prior pin was `29d1c32955347e0534dbb80acea9a9a8b1d6a4f1`; the re-pin diff only added the host runtime probe helper/playbook. No task was submitted by that invocation. No host lifecycle operation was executed in Slice 48; broker operations remain read-only because exact rollback qualification is not established.
- Owner-authenticated trace fetch, actual owner/Pi turn continuity, and Semaphore-managed host lifecycle/restart/rollback remain release gates. No public, LAN, Tailnet, NodePort, LoadBalancer, or AWP Ingress route was added.

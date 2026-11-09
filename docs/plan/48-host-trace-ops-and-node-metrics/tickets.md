# Slice 48 tickets

- [x] Inspect current Slice47 deployment and record spec/decisions before mutation.
- [x] Probe trace-explainer health from the host and v2 pod separately; host LaunchAgent state is recorded in the existing Semaphore read-only probe inventory.
- [x] Wire Edge to the verified private trace-explainer endpoint and roll out; owner-authenticated fetch remains gated on identity.
- [x] Render a synthetic trace and verify completed status, manifest, and MP4 fetch through the worker; Edge owner fetch returned 403 without owner identity and was not bypassed.
- [x] Leave broker lifecycle read-only: exact rollback behavior is not yet qualified; no task was run.
- [x] Deploy Fluent Bit k3d-node metrics with `system=k3d-node`; PromQL `up{job="fluent-bit-k3d-node"}` returned 1 and `node_cpu_seconds_total{system="k3d-node"}` returned series.
- [x] Leave host-origin metrics unclaimed; no qualified loopback-only exporter is configured.
- [x] Verify all 11 v2 pods 1/1, `/readyz`, Prometheus readiness, gateway PromQL, and private ingress boundaries after changes.
- [x] Verify v1 Compose remains down (remaining exited containers only) and all named volumes remain present.
- [x] Record remaining owner identity/live Pi turn gates without weakening auth.

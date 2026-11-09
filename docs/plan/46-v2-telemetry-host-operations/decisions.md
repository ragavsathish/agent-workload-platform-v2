# Decisions

- v2 telemetry must use the dedicated OTEL-LGTM instance in the v2 k3d namespace. v1's local collector is not acceptable evidence.
- Collector `/-/ready`, Grafana health, and synthetic OTLP gauges are transport/stack checks only. Acceptance needs service-originated AWP signals.
- PromQL must use explicit environment and service labels, bounded cardinality, and meaningful semantics. Do not include user, session, task, prompt, repository, or artifact identifiers.
- k3d-pod-to-host connectivity and Semaphore-runner-to-host/k3d connectivity are distinct network paths and require separate evidence.
- Host-native Pi, MLX inference, and Gondolin operations are managed through the native Semaphore runner. The runner's host network does not make pods host-networked.
- Begin Semaphore host operations read-only. Enable only exact-target, bounded lifecycle tasks after inventory, side effects, verification, and rollback are reviewed.
- No v1 shutdown, app cutover, or public/tailnet route is part of this slice.

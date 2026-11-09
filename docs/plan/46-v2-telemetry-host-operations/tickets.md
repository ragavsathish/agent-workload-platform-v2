# Tickets

- [x] Inspect existing v2 deployment and Slice45 telemetry/runner evidence.
- [x] Record telemetry, networking, and host-operation decisions before implementation.
- [x] Identify operational signals actually emitted by the gateway, Grain, and host-native services.
- [x] Implement bounded real AWP v2 gateway metrics and labels where missing.
- [x] Query the dedicated v2 Prometheus and capture expected PromQL results.
- [x] Qualify Semaphore runner read-only status and local k3d API access.
- [x] Qualify pod-to-host independently using a harmless TCP-only probe; application health remains gated.
- [ ] Enable only exact-target host lifecycle tasks with verification and rollback.
- [x] Confirm v1 remains running and no public/tailnet AWP route exists.
- [x] Record remaining deployment and cutover gaps.

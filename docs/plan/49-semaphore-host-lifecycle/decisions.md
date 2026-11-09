# Decisions — Slice 49

- Semaphore is the operator control plane for host-native services; k3d deploys the v2 application and support services.
- Scope is exactly one native service: `com.sathish.pipecat-broker`. No generic service-name input or arbitrary shell command is allowed.
- The Semaphore runner is `macos-host`, using host networking. V2 app and support Kubernetes Services remain ClusterIP.
- `--check` and the initial preflight are read-only. No token, environment variable, PID, or command argument value is published in task output.
- Restart is conditional on an authenticated, reliable no-active-turn signal. If this cannot be established, ship an operator-ready guarded template but do not execute a restart.
- Rollback restores the previously loaded state by kicking the same exact target and verifying both health paths. No v1 Compose service or volume is touched.
- Host-native Prometheus metrics are not added unless a private, bounded host-to-k3d path already exists; do not open ports solely for this slice.
- No owner credential is inferred or fabricated. Live owner viewer/voice qualification remains explicitly gated.

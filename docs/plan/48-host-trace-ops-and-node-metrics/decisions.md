# Slice 48 decisions

1. All AWP app services remain ClusterIP-only; temporary local port-forwards are verification tools, not routes.
2. Native host services are inspected and operated through the registered Semaphore `macos-host` runner.
3. A host lifecycle task must target an exact reviewed LaunchAgent label and include before/after readiness checks plus rollback; no broad `launchctl` pattern matching.
4. The trace-explainer MCP stays bound to loopback on macOS. The k3d pod may use Docker Desktop's host bridge only after a status-only probe proves it.
5. Synthetic trace inputs must contain no owner or real session data.
6. Fluent Bit metrics from a k3d node use `system=k3d-node`; the legacy Docker Desktop VM metric stream remains a distinct signal and is not reused as deployment evidence.
7. No v1 Compose restart or volume removal is in scope; v1 stays down.


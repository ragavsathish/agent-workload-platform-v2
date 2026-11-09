# Ticket 01: provision the local Semaphore control plane

## Boundary

Create a dedicated k3d cluster and run version-pinned Semaphore UI, Grain, gateway, Edge/Flutter, Gitea, artifact storage, Oxigraph, Grafana MCP, and Prometheus/OTLP workloads. Expose the operator UI and owner client only through host loopback ports. Grain's SQLite/LMDB event state and stateful support-service data must use persistent storage independent of the cluster lifecycle. Keep cluster identity, host ports, and state separate from v1, the current v2 candidate, and other Docker projects.

## Acceptance

- Preflight detects missing k3d, Docker, port availability, and cluster-name collisions before creation.
- Semaphore UI starts with an operator-controlled credential and persistent storage.
- The host UI route binds to `127.0.0.1`; no public ingress or tailnet route is created.
- Restarting the pod and cluster retains the configured user and task history.
- Semaphore's AWP project uses a pinned v2 repository revision and reviewed templates only.
- Grain, gateway, and Edge/Flutter readiness checks pass from the intended cluster/service boundaries.
- Recreating application pods, and a controlled k3d restart rehearsal, preserve all Grain event-store identities and owner/task continuity.
- The Flutter owner entry continues to return its existing app assets.
- Cluster workloads can reach required host-only Pi/broker APIs only through an authenticated bridge bound to the Docker Desktop internal host-reachable interface; direct host `127.0.0.1` assumptions fail closed.
- Host-side Pi/broker services reach k3d support services through loopback-only host ports, and the v2 host collector export is visible to k3d Prometheus.
- Neither the host bridge nor the owner Edge route binds a public or LAN interface.

## Evidence

Record k3d and Semaphore versions, cluster identity, loopback binding, persistence test, bootstrap path, and one harmless authenticated UI/API check.


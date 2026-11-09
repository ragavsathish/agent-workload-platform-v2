# Slice 39 decisions

## D01: Semaphore is the operator UI

Semaphore UI is the deployment and operations front end. The existing Flutter application remains the owner-facing voice workspace. These interfaces have different users, permissions, and acceptance evidence.

## D02: Keep Semaphore local and private

The k3d cluster and Semaphore UI are local development operations infrastructure. Bind the host entry to loopback and do not create public or tailnet exposure in this slice.

## D03: Use a native macOS remote runner for host operations

The user requested host networking for the Ansible runner. Run Semaphore's remote runner natively on macOS with its local executor. This provides the actual macOS host network, launchd and filesystem authority needed to manage Pi voice, MLX inference, and the Gondolin scheduler. A Docker Desktop host-networked container or k3d pod with `hostNetwork` is not equivalent to host process authority. Before mutating playbooks are enabled, the native runner must pass explicit host-endpoint and k3d API probes.

## D04: Deploy all Linux-compatible AWP services and support infrastructure in k3d

K3d runs Grain (all Grain-backed domain models), the Python gateway, Edge/Flutter, Gitea, artifact storage, Oxigraph, Grafana MCP, Prometheus/OTLP, and Semaphore UI. Grain SQLite and LMDB and any stateful support services use persistent storage with stable identity. Native macOS Ansible runner and the MLX broker, Pi voice service, and Gondolin task scheduler remain host-side and are managed through Semaphore. Current implementation uses Apple's MLX and creates Gondolin VMs while mounting host skills/workspaces; moving those functions into Linux pods needs replacement runtimes and separate qualification. Host↔k3d service access uses independent, explicitly probed paths; the runner's host networking is not a substitute for the pod-to-host bridge.

## D05: Grain remains the owner and work authority

Semaphore and Ansible may inspect deployment state and request reviewed deployment operations. They do not create an alternate source of truth for owner sessions, tasks, turns, or knowledge events. AWP's existing Grain-backed commands and projections retain those responsibilities.


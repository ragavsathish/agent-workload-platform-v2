# Slice 39: local k3d operations through Semaphore UI

Cites the [public v2 candidate](../22-public-v2-candidate/plan.md), the [deployment runbook](../../../impl/deployment/README.md), the [Semaphore UI source](https://github.com/semaphoreui/semaphore), the [Semaphore runner guide](https://semaphoreui.com/docs/admin-guide/runners), the [Docker Desktop host networking guide](https://docs.docker.com/engine/network/drivers/host/#docker-desktop), and the [k3d service exposure guide](https://k3d.io/stable/usage/exposing_services/).

## Goal and boundary

Deploy the Kubernetes-compatible AWP v2 control plane and Edge in a dedicated local k3d cluster, with a loopback-only Semaphore operator UI. Semaphore runs reviewed Ansible deployment and verification tasks for both k3d workloads and the required macOS host services. The owner-facing Flutter workspace remains the AWP frontend, and Grain remains the authority for owner turns, tasks, and durable state.

This slice moves the control plane and browser Edge from the current host/Compose topology into k3d. It also defines the host boundary required by this repository's Apple Silicon and Gondolin runtime. The Grain service (sessions, tasks, knowledge, songs, incidents, and deployment projections), gateway, and Edge/Flutter assets run in k3d. The Grain SQLite and LMDB files use persistent storage independent of pod and cluster lifecycle. The Pi voice service, MLX broker, and scheduler that creates Gondolin VMs stay native on macOS and are installed, restarted, stopped, and checked by Semaphore's host runner. K3d must use a cluster identity and ports separate from v1, the current v2 candidate, and unrelated Docker projects.

| Component | Placement | Reason / contract |
| --- | --- | --- |
| Grain service and event models | k3d | Clojure service; mount SQLite and LMDB directories through stable persistent storage. |
| Gateway | k3d | Python owner/task API gateway; route only from Edge and local operator probes. |
| Edge and Flutter assets | k3d | Owner WebRTC/HTTP entry and built browser client; bind host entry to loopback. |
| Semaphore server and its database | k3d | Local operations UI and task history; private bootstrap credentials and persistent data. |
| Gitea, artifact object store, Oxigraph, Grafana MCP | k3d | AWP voice/task integrations used by Pi/Gondolin; use private service endpoints and persistent data where required. |
| Prometheus and OTLP collector | k3d for v2 | Give v2 a separate collector/query endpoint; keep the old collector only during cutover, then stop it after v2 metric flow is confirmed. |
| Pi voice service | macOS host, managed by Semaphore | Creates Gondolin voice VM and uses Pi model credentials, host skills, and persisted host session files. |
| Inference broker | macOS host, managed by Semaphore | Uses Apple MLX STT/TTS on Apple Silicon and intentionally binds loopback for model/child-process behavior. |
| Task scheduler / Gondolin worker | macOS host, managed by Semaphore | Starts Gondolin task VMs and maps prepared host workspaces and private task/session state. |

The two network directions are separate from runner networking. Native Pi/broker services reach k3d Gitea, artifact, Oxigraph, Grafana MCP, and telemetry through host ports bound only to `127.0.0.1`. K3d pods reach Pi/broker APIs through a narrowly scoped, authenticated host bridge bound only to Docker Desktop's internal host-reachable interface. Host networking on the native runner solves neither pod-to-host routing nor service authentication. Prove both routes from the actual service namespaces before enabling AWP owner routes. Never bind host service APIs to public or LAN interfaces as a shortcut.

Semaphore may run Ansible only from a pinned AWP v2 revision and reviewed task templates. The operator UI is bound to loopback. No public or tailnet route is enabled by this slice. The AWP owner Edge route continues to serve the Flutter client and remains subject to its existing independent public qualification gates.

## Acceptance

- A named, dedicated k3d cluster hosts Semaphore UI, Grain, gateway, and Edge/Flutter with persistent application data and non-default, privately supplied Semaphore administrator credentials.
- K3d also hosts the AWP support services needed by Pi/Gondolin and v2 metrics (Gitea, artifact object store, Oxigraph, Grafana MCP, Prometheus/OTLP collector) with separate project identity and durable data where required.
- The Semaphore UI host port is loopback-only. Its initial configuration does not create a public ingress or tailnet Serve route.
- Semaphore launches approved Ansible tasks from a pinned repository revision and retains task status and output.
- Grain-owned SQLite and LMDB event state is mounted on persistent storage with stable identity across pod restarts and cluster recreation; application pods cannot cause Semaphore to initialize replacement state silently.
- Pi voice, MLX inference, and Gondolin-backed task execution remain native macOS services managed through the Semaphore runner, with their own state and credentials outside the cluster.
- The native runner's actual host-network reachability is verified with harmless probes to the loopback Semaphore endpoint, macOS host services, and k3d API. A failed probe blocks mutating deployment tasks.
- The documented network boundary distinguishes the macOS host, Docker Desktop's Linux VM, the k3d node containers, and Kubernetes pod networks. `localhost` is never assumed to cross these boundaries without a passing probe.
- Existing Flutter owner pages continue to be served by AWP Edge. Semaphore is solely the operator and deployment UI.
- Prometheus/OTel runs in k3d for v2. PromQL isolates v2 with `deployment_environment_name="awp-v2-local"` and returns both k3d and macOS-host service series without counting v1 series.
- v1 remains running until v2 owner, task, readiness, and rollback evidence passes the reviewed cutover gate. No failure exposes the candidate or loses Grain state.
- Restarting Semaphore, application pods, or k3d cannot delete or reinitialize AWP's Grain event store or persistent state directory.

## Architecture and constraints

Semaphore UI is distinct from its runner. Semaphore's official docs support a remote runner that polls the server; without a remote runner, the UI server executes jobs in its own filesystem and network context. Runner location and executor type are separate choices. Use one native macOS remote runner with the local executor for host service lifecycle operations; it runs on the macOS host and therefore has host networking and the required launchd/filesystem access. Ansible should use a local host inventory for those operations. The runner may use a separately scoped kubeconfig to manage the k3d namespace. Do not use a pod `hostNetwork` or Docker `network_mode: host` as a substitute: neither grants macOS process or filesystem authority.

The chosen user requirement is “Ansible runner uses host networking.” Satisfy it with the native macOS runner process, whose process and network context are the actual host. Prove from that runner that it can reach the loopback Semaphore endpoint, the configured macOS service endpoints, and the k3d API. Do not silently reinterpret `host` as `ansible_connection=network_cli`; AWP deployment targets are not network appliances.

K3d and Ansible CLI are not present on the host at specification time; `kubectl` is present. Tool installation, cluster creation, Semaphore image/version pinning, and persistent storage setup belong to implementation slices after this spec is approved. The currently running AWP v2 local stack and v1 services remain unchanged while specifying and implementing the control plane.

## Non-goals

- Replacing the Flutter voice workspace with Semaphore UI.
- Moving Apple MLX or Gondolin VM execution into k3d in this slice; these remain macOS host services managed by Semaphore.
- Enabling a public or tailnet route for Semaphore or qualifying the public AWP journey.
- Allowing Semaphore to mutate Grain state, accept owner turns, or act as a second command/query authority.
- Managing network appliances through Ansible's `network_cli` connection plugin.

## Deployment gate

The deployment includes all Linux-compatible AWP services and support dependencies in k3d. The native host owns the MLX broker, Pi's Gondolin voice session, and Gondolin-backed task execution; Semaphore manages these as host services. A literal move of those three processes into k3d is unsupported by the current implementation: the broker imports Apple MLX and binds loopback for child-process behavior, while Pi and the scheduler create Gondolin VMs and mount host skill/workspace paths. A Linux pod's `hostNetwork` does not provide Apple's MLX framework, macOS hypervisor access, or host paths. Replacing MLX/Gondolin to execute those processes inside k3d would need a separate runtime design and new media/task qualification; this spec does not make that unsupported claim.

The host bridge described above is a release blocker: the existing host APIs bind to `127.0.0.1`, which is correct for the current all-native topology but is not directly reachable by k3d pods. The operator runner's host network does not solve pod-to-host routing. Deployment must first create and probe an authenticated, internal-only bridge without changing the app APIs' bind address to `0.0.0.0`. The reverse host-to-k3d ports must remain loopback-bound.

Before enabling mutating Semaphore templates, the native macOS runner must prove both host lifecycle control and k3d API access. A failed host or API probe blocks deploy and rollback tasks.

## Tickets

- [Ticket 01: provision the local Semaphore control plane](ticket-01-semaphore-k3d.md)
- [Ticket 02: qualify runner networking and Ansible targets](ticket-02-host-network-runner.md)
- [Ticket 03: add AWP readiness, PromQL, and cutover tasks](ticket-03-awp-operations.md)

## Exit evidence

Capture selected cluster and image versions, loopback bindings, Grain and support-service persistent-volume identities, administrator bootstrap outcome, runner location/executor, both host↔k3d application path probes and the runner-to-k3d API probe, pinned playbook revision, harmless Semaphore task log, in-cluster readiness, host-service health, PromQL output for `awp-v2-local`, owner/task continuity evidence, and a reversible cutover/rollback record. Keep public owner/media qualification as a separate gate. Do not stop v1 until the cutover decision records passing v2 evidence and the owner-authority transition.

